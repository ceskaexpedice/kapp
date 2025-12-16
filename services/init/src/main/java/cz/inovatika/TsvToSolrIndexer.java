package cz.inovatika;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
//import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
//import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
//import java.util.Arrays;
import java.util.*;
//import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import cz.inovatika.authors.AuthorData;
import cz.inovatika.authors.AuthorDataUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

//TODO: Refactor !!
public class TsvToSolrIndexer {

    private static final String SOLR_URL = "http://localhost:8983/solr";
    public static final String MODEL_FIELD = "model";
    public static final String ROOT_PID_FIELD = "root.pid";
    public static final String ROOT_TITLE_FIELD = "root.title";
    public static final String AUTHORS_KRAMERIUS_FIELD = "authors_kramerius";
    public static final String NAME_IDENTIFIER_FIELD = "nameIdentifier";
    private static final Logger log = LoggerFactory.getLogger(TsvToSolrIndexer.class);

    private static String DIRECTORY_PATH = "../Data"; // change this

    private static SolrClient solrClient;
    private final static boolean VERBOSEDEBUG = false;

    public static void main(String[] args) throws ParserConfigurationException {
        if (args.length > 0) {
            DIRECTORY_PATH = args[0];
        }
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        HttpClient client = HttpClient.newHttpClient();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();

        boolean show_comma_info = false;
        File folder = new File(DIRECTORY_PATH);
        if (!folder.isDirectory()) {
            System.err.println("Invalid directory: " + DIRECTORY_PATH);
            return;
        }
        // before looping files, load config:
        Path mappingsPath = Path.of("services/init/src/main/resources/tsv-mappings.json");
        ConfigMappingsLoader mappingsLoader;
        try {
            mappingsLoader = new ConfigMappingsLoader(mappingsPath);
        } catch (IOException e) {
            System.err.println("Failed to load TSV mappings: " + e.getMessage());
            return;
        }
        solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build();
        try {
            File[] tsvFiles = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".tsv"));

            if (tsvFiles == null || tsvFiles.length == 0) {
                System.out.println("No TSV files found in directory.");
                return;
            }

            for (File tsvFile : tsvFiles) {
                System.out.println("Processing: " + tsvFile.getName());
                CSVFormat format = CSVFormat.TDF.builder()
                        .setHeader() // Auto-detect header from first row
                        .setSkipHeaderRecord(true)
                        .build();
                try (Reader reader = new FileReader(tsvFile); CSVParser parser = new CSVParser(reader, format)) {
                    Map<String, String> headers = mappingsLoader.getHeadersForFile(tsvFile.getName());
                    if (headers == null) {
                        System.out.println("No configuration for this file: " + tsvFile.getName());
                        continue;
                    }
                    for (CSVRecord record : parser) {
                        SolrInputDocument doc = new SolrInputDocument();
                        int recordNumber = (int) record.getRecordNumber() + 1; // +1 to account for header
                        String pid;
                        String rootPid;
                        String[] authorArray;
                        AuthorData[] nameIDArray = null;
                        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
                            String header = headers.get(entry.getKey());
                            if (header == null) {
                                continue;
                            }
                            String value = entry.getValue();
                            //String Originalvalue = value;
                            try {
                                /*
                                 * if("1926-".equals(value)){
                                 * System.out.println("Debug");
                                 * }
                                 */
                                switch (header) {
                                    case "authors" -> {
                                        authorArray = value.split(" {2}|\\|");
                                        if (authorArray.length > 0 && !authorArray[0].isEmpty()) {
                                            doc.addField(header, authorArray);
                                        }
                                    }
                                    case "imported" -> {
                                        if (value != null && !value.isEmpty()) {
                                            String[] imported = value.split("\\.");
                                            doc.addField(header,
                                                    String.format("%04d-%02d-%02dT00:00:00Z",
                                                            Integer.valueOf(imported[2]), Integer.valueOf(imported[1]),
                                                            Integer.valueOf(imported[0])));
                                        } else {
                                            System.err.println("Empty imported date on row: " + recordNumber);
                                        }
                                    }
                                    case "pid" -> {
                                        String[] parts = value.trim().replaceAll("\\|", " ").split("\\s+");
                                        for (int i = 0; i < parts.length; i++) {
                                            if (!parts[i].startsWith("uuid:")) {
                                                parts[i] = "uuid:" + parts[i];
                                            }
                                        }
                                        pid = parts[0];
                                        rootPid = parts[0];

                                        if (parts.length > 1) {
                                            if (parts.length > 2) {
                                                System.out.println("More than two UUIDs found, using first two."
                                                        + parts[2] + " Row: " + recordNumber);
                                            }
                                            pid = parts[1];
                                            rootPid = parts[0];
                                            // System.out.println("Multiple UUIDs found, First one: " + uuid + " Second
                                            // one:" + secondPid + " Row: " + recordNumber);
                                        }
                                        
                                        doc.setField(header, pid);
                                        doc.setField(ROOT_PID_FIELD, rootPid);

                                        // NDK. K5
                                        Document xml = getBiblioMods(client, builder, pid,
                                                "https://ndk.cz/search/api/v5.0/item/" + pid + "/streams/BIBLIO_MODS");
                                        if (xml == null) {
                                            // MZK. K7
                                            // https://api.kramerius.mzk.cz/search/api/client/v7.0/items/uuid:4f0d2790-37d6-11e6-8746-005056825209/metadata/mods
                                            xml = getBiblioMods(client, builder, pid,
                                                    "https://api.kramerius.mzk.cz/search/api/client/v7.0/items/" + pid
                                                            + "/metadata/mods");
                                        }

                                        //nameIDArray =  XmlProcessor.processBiblioMods(xml, doc);
                                        nameIDArray = AuthorDataUtils.parseAuthorData(xml).toArray(AuthorData[]::new);

                                        // String query = "q=pid:" + URLEncoder.encode(
                                        // ClientUtils.escapeQueryChars(pid), "UTF-8") ;
                                        // JSONObject item = getItem(client, builder, pid,
                                        // "https://ndk.cz/search/api/v5.0/search?" + query);
                                        JSONObject item = getItem(client, pid,
                                                "https://ndk.cz/search/api/v5.0/item/" + pid);
                                        if (item != null) {
                                            doc.setField(MODEL_FIELD, item.optString("model"));
                                            doc.setField(ROOT_PID_FIELD, item.optString("root_pid"));
                                            doc.setField(ROOT_TITLE_FIELD, item.optString("root_title"));
                                        } else {
                                            item = getItem(client, pid,
                                                    "https://api.kramerius.mzk.cz/search/api/client/v7.0/search?q=pid:" + URLEncoder.encode(ClientUtils.escapeQueryChars(pid), StandardCharsets.UTF_8));
                                                    if (item != null && !item.getJSONObject("response")
                                                            .getJSONArray("docs").isEmpty()) {
                                                doc.setField(MODEL_FIELD, item.getJSONObject("response")
                                                        .getJSONArray("docs").getJSONObject(0).optString("model"));
                                                doc.setField(ROOT_PID_FIELD, item.getJSONObject("response")
                                                        .getJSONArray("docs").getJSONObject(0).optString("root.pid"));
                                                doc.setField(ROOT_TITLE_FIELD, item.getJSONObject("response")
                                                        .getJSONArray("docs").getJSONObject(0).optString("root.title"));
                                            }
                                        }
                                    }
                                    case "year_of_death_last_author", "public_since" -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4) {
                                            doc.addField(header, value);
                                        } /*else {
                                             if(!"?".equals(value))System.err.println("Unexpected year value on row: "
                                             + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ?
                                             Originalvalue : Originalvalue + " _ " + value));
                                        }*/
                                    }
                                    default ->
                                        doc.addField(header, value);
                                }

                            } catch (IndexOutOfBoundsException e) {
                                System.err.println("Caught OOBexception in" + tsvFile.getName() + ": " + e.getMessage()
                                        + "\n\tVALUE: " + /*Original*/value);
                            }
                        }
                        parsePublicString(doc, recordNumber);
                        setState(doc);
                        ParseDates(doc, getField(doc, "date.str"), recordNumber, show_comma_info);
                        assert nameIDArray != null;
                        XmlProcessor.trySetLastAuthor(doc, Arrays.asList(nameIDArray));

                        Arrays.asList(nameIDArray).forEach(author -> {
                            doc.addField(AUTHORS_KRAMERIUS_FIELD, author.toJsonString());
                            if (author.getNameIdentifier() != null && !author.getNameIdentifier().isEmpty()) {
                                doc.addField(NAME_IDENTIFIER_FIELD, author.getNameIdentifier());
                            }
                        });

                        for (SolrInputDocument authorDoc : Arrays.stream(nameIDArray).map(AuthorData::toAuthoritySolrDocument).toList()) {
                            if (authorDoc != null)
                                solrClient.add("authors", authorDoc);
                        }
                        if(doc.get("pid")==doc.get("root.pid")){
                            doc.addField(ROOT_TITLE_FIELD, doc.get("title"));
                        }
                        //doc.addField("state_user", "TableParser");
                        solrClient.add("titles", doc); 
                    }

                } catch (Exception e) {
                    System.out.println("Critical Exception: in file:" + tsvFile.getName() + "of type:" + e.getClass()
                            + ": " + e.getMessage());
                    // sent_request = true;
                }
            }

            solrClient.commit("titles");
            solrClient.commit("authors");
            System.out.println("All TSV files indexed successfully.");
            System.exit(0);

        } catch (ConnectException e) {
            System.err.println("Failed to connect to Solr " + e.getMessage());
            System.exit(1);
        } catch (IOException | SolrServerException e) {
            System.err.println(
                    "Error indexing data: Exception type: " + e.getClass().getName() + " Message:" + e.getMessage());
            System.exit(1);
        }
    }

    private static String getField(SolrInputDocument doc, String fieldName) {
        SolrInputField field = doc.getField(fieldName);
        return field != null ? (String) field.getValue() : null;
    }

    private static void parsePublicString(SolrInputDocument doc, int recordNumber) {
        final String value = getField(doc, "public_string");
        if (value == null) {
            return;
        }
        final String header = "public";
        switch (value.toLowerCase()) {
            case "ano", "yes", "true" ->  {
                doc.setField(header, true);
            }
            case "ne", "no", "false" ->  {
                doc.setField(header, false);
            }
            default -> {
                if(VERBOSEDEBUG) System.err.println("Unexpected boolean value on row: " + recordNumber + "\n\tVALUE: " + value);
            }
        }
    }

    private static void setState(SolrInputDocument doc) {
        String val;
        if (doc.containsKey("public")) {
            val = (boolean) doc.getFieldValue("public") ? "controlled_public" : "controlled_private";
        } else if (doc.containsKey("public_since")) {
            int public_since = Integer.parseInt(doc.getFieldValue("public_since").toString());
            if (public_since > LocalDate.now().getYear()) {
                val = "controlled_private"; // KONTROLOVANÉ- NEVEŘEJNÉ
            } else {
                val = "controlled_public"; // KONTROLOVANÉ - VOLNÉ
            }
        } else {
            val = "controlled_unknown"; // KONTROLOVANÉ - NEZNÁMÉ
        }

        doc.setField("state", val);

        JSONObject h = new JSONObject()
                .put("date", LocalDateTime.now().toString())
                .put("reason", "import from excel")
                .put("user", "importer")
                .put("state", val);

        if (documentExists((String) doc.getFieldValue("pid"))) {
            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("add", h.toString());
            doc.addField("state_history", partialUpdate);
        } else {
            doc.addField("state_history", h.toString());
        }

    }

    private static boolean documentExists(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setRows(0);
        try {
            return solrClient.query("titles", q).getResults().getNumFound() > 0;
        } catch (SolrServerException | IOException e) {
            log.error(String.valueOf(e));
            return false;
        }
    }

    private static Document getXml(DocumentBuilder builder, HttpResponse<String> response) throws IOException {
        try {
            Document xml = builder.parse(new InputSource(new StringReader(response.body())));
            xml.getDocumentElement().normalize();
            return xml;
        } catch (SAXException e) {
            System.err.println("Failed to parse MODS XML: " + e.getMessage());
            System.err.println("Response body:\n" + response.body());
            return null;
        }
    }

    private static JSONObject getItem(HttpClient client, String pid, String url)
            throws IOException, InterruptedException {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response != null && response.statusCode() == 200) {
            return new JSONObject(response.body());
        } else {
            System.out.println("Failed to fetch ITEM for root pid " + pid + ", response code: "
                    + (response != null ? response.statusCode() : "null response"));
            return null;
        }
    }

    private static Document getBiblioMods(HttpClient client, DocumentBuilder builder, String pid, String url)
            throws IOException, InterruptedException {
        if (pid == null || pid.isBlank()) {
            return null;
        }
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response != null && response.statusCode() == 200) {
            return getXml(builder, response);
        } else {
            System.out.println("Failed to fetch MODS for pid " + pid + ", response code: "
                    + (response != null ? response.statusCode() : "null response"));
            return null;
        }
    }

    private static void ParseDates(SolrInputDocument doc, String value, int recordNumber, boolean show_comma_info) {
        try {
            String Originalvalue = value;
            /*
             * if("[191-?]".equals(value)){
             * System.out.println("Debug");
             * }
             */
            if (value == null || value.isEmpty()) {
                System.err.println("Empty date on row: " + recordNumber);
                return;
            }
            value = XmlProcessor.replaceWordsWithQMarks(value, recordNumber, Originalvalue);
            
            if (value.contains(",")) {
                String og = value;
                value = value.split(",")[0].trim();
                if (show_comma_info) {
                    System.out
                            .println("Comma found in " + og + ", using first part: " + value + " Row: " + recordNumber);
                }
            }
            if (value.contains("19--") && value.length() <= 6) {// Handle 19-- format with or without []
                value = "1900?-1999?";
            } else if (value.contains("19-") && value.length() <= 5) {// Handle 19- format with or without []
                value = "1900?-1999?";
            }
            value = value.replaceAll("19(\\d)-", "19$10?-19$19?");
            if (value.length() == 5) {// 1999] or [1999
                if (value.endsWith("]")) {
                    value = value.substring(0, 4) + '-' + value;
                } else if (value.startsWith("[")) {
                    value = value + '-' + value.substring(1);
                }
            }
            value = value.replaceAll("\\[?19--\\??]?", "19");
            value = value.replaceAll("\\s+až\\s+", "-");// replace až with dash
            String kolem_fix = value.replaceAll("kolem r.\\s+", "?");// replace kolem with ?
            value = value.equals(kolem_fix) ? value : kolem_fix + "?";
            value = value.replaceAll("\\s+a\\s+", "-");// replace a with dash
            // value = value.replaceAll("[\\[\\]]", "");//Just purge the brackets.
            XmlProcessor.parseDateBasic(doc, value, recordNumber, Originalvalue);

        } catch (Exception e) {
            System.out.println("Critical Exception occurred in:date_str: " + e.getMessage());
        }
    }

    public static String normalize(String input) {
        return XmlProcessor.normalize(input);
    }

}
