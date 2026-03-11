package cz.inovatika;

import cz.inovatika.Constants.Collection;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.Labels;
import cz.inovatika.authors.AuthorData;
import cz.inovatika.authors.AuthorDataUtils;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.workflow.LicensesAssignation;
import cz.inovatika.workflow.States;
import cz.inovatika.workflow.Workflow;
import cz.inovatika.workflow.WorkflowDoc;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

//TODO: Refactor !!
public class TsvToSolrIndexer {

    private static final String SOLR_URL = "http://localhost:8983/solr";//"http://solr-kappScheduler:8983/solr";//

    private static String DIRECTORY_PATH = System.getProperty("user.home") + "/.kapp/Data";//"../Data"; // change this
    private static final String TSV_MAPPINGS = System.getProperty("user.home") + "/.kapp/tsv-mappings.json";//"services/init/src/main/resources/tsv-mappings.json"
    private final static SolrClient solrClient = new Http2SolrClient.Builder(SOLR_URL).withConnectionTimeout(100, TimeUnit.SECONDS).build();
    static ApplicationConfig applicationConfig;

    private final static String MZKLink = "https://api.kramerius.mzk.cz/search/api/client/v7.0/";

    public static void main(String[] args) throws ParserConfigurationException, FileNotFoundException {
        if (args.length > 0) {
            DIRECTORY_PATH = args[0];
        }
        InputStream resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
        applicationConfig = ApplicationConfig.loadFromStream(resourceAsStream);
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
        ConfigMappingsLoader mappingsLoader;
        try {
            mappingsLoader = new ConfigMappingsLoader(Path.of(TSV_MAPPINGS));
        } catch (IOException e) {
            System.err.println("Failed to load TSV mappings: " + e.getMessage());
            return;
        }
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
                        SolrInputDocumentWrapper doc = new SolrInputDocumentWrapper(
                                AppDataFields.PID_FIELD,
                                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);
                        doc.addField(AppComputedFields.LABELS_FIELD, Labels.EXCEL);
                        int recordNumber = (int) record.getRecordNumber() + 1; // +1 to account for header
                        String pid = null;
                        String rootPid = null;
                        String model = null;
                        String[] authorArray;
                        AuthorData[] nameIDArray = null;
                        int decision_date = -1;
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
                                    /*case "imported" -> {
                                        if (value != null && !value.isEmpty()) {
                                            String[] imported = value.split("\\.");
                                            doc.addField(header,
                                                    String.format("%04d-%02d-%02dT00:00:00Z",
                                                            Integer.valueOf(imported[2]), Integer.valueOf(imported[1]),
                                                            Integer.valueOf(imported[0])));
                                        } else {
                                            System.err.println("Empty imported date on row: " + recordNumber);
                                        }
                                    }*/
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
                                        //doc.setField(KramFields.ROOT_PID_FIELD, rootPid);

                                        // NDK. K5
                                        Document xml = getBiblioMods(client, builder, pid,
                                                "https://ndk.cz/search/api/v5.0/item/" + pid + "/streams/BIBLIO_MODS");
                                        if (xml == null) {
                                            // MZK. K7
                                            // https://api.kramerius.mzk.cz/search/api/client/v7.0/items/uuid:4f0d2790-37d6-11e6-8746-005056825209/metadata/mods
                                            xml = getBiblioMods(client, builder, pid,
                                                    MZKLink + "items/" + pid
                                                            + "/metadata/mods");
                                        }

                                        //nameIDArray =  XmlProcessor.processBiblioMods(xml, doc);
                                        if (xml != null)
                                            nameIDArray = AuthorDataUtils.parseAuthorData(xml).toArray(AuthorData[]::new);

                                        // String query = "q=pid:" + URLEncoder.encode(
                                        // ClientUtils.escapeQueryChars(pid), "UTF-8") ;
                                        // JSONObject item = getItem(client, builder, pid,
                                        // "https://ndk.cz/search/api/v5.0/search?" + query);

                                        JSONObject item = getItem(client, pid,
                                                "https://ndk.cz/search/api/v5.0/item/" + pid);
                                        if (item != null) {
                                            doc.setField(AppDataFields.MODEL_FIELD, item.optString("model"));
                                            model = item.optString("model");
                                            //doc.setField(AppDataFields.ROOT_PID_FIELD, item.optString("root_pid"));
                                            rootPid = item.optString("root_pid");
                                            //doc.setField(AppDataFields.ROOT_TITLE_FIELD, item.optString("root_title"));
                                        } else {
                                            item = getItem(client, pid,
                                                    MZKLink + "search?q=pid:" + URLEncoder.encode(ClientUtils.escapeQueryChars(pid), StandardCharsets.UTF_8));
                                            if (item != null) {
                                                var docs = item.getJSONObject("response").getJSONArray("docs");
                                                if (!docs.isEmpty()) {
                                                    var obj = docs.getJSONObject(0);
                                                    doc.setField(AppDataFields.MODEL_FIELD, obj.optString("model"));
                                                    model = obj.optString("model");
                                                    //doc.setField(AppDataFields.ROOT_PID_FIELD, obj.optString("root.pid"));
                                                    rootPid = obj.optString("root.pid");
                                                    //doc.setField(AppDataFields.ROOT_TITLE_FIELD, obj.optString("root.title"));
                                                }
                                            }
                                        }
                                    }
                                    case "year_of_death_last_author"/*, "public_since"*/ -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4) {
                                            decision_date = Integer.parseInt(value);
                                            //doc.addField(ApplFields.DECISION_DATE, value);
                                        } /*else {
                                             if(!"?".equals(value))System.err.println("Unexpected year value on row: "
                                             + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ?
                                             Originalvalue : Originalvalue + " _ " + value));
                                        }*/
                                    }/*
                                    case "public_since" -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4)
                                            decision_date = Integer.parseInt(value);
                                    }*/
                                    case "date.str" -> {
                                        doc.addField(AppComputedFields.DATE_ISSUED_STRING, value);
                                        ParseDates(doc, value, recordNumber, show_comma_info);
                                    }
                                    default -> {
                                    }//doc.addField(header, value);
                                }

                            } catch (IndexOutOfBoundsException e) {
                                System.err.println("Caught OutOfBoundsException in" + tsvFile.getName() + ": " + e.getMessage()
                                        + "\n\tVALUE: " + /*Original*/value);
                            }
                        }

                        States desiredState = parsePublicString(doc, recordNumber);
                        //if (desiredState == States.CONTROLLED_UNKNOWN) desiredState = setState(doc,pid);
                        SolrQuery q = new SolrQuery()
                                .setQuery("pid:\"" + pid + "\"")
                                .setRows(1);
                        try {
                            States old_state;
                            SolrDocumentList results = solrClient.query(Collection.TITLES_COLLECTION, q).getResults();
                            if (results.getNumFound()==0){
                                old_state = States.TO_CONTROL;
                            }
                            else {
                                SolrDocument SolrDoc = results.getFirst();
                                Object old_state_obj = SolrDoc.getFieldValue(AppComputedFields.STATE_FIELD);
                                if (old_state_obj == null) {
                                    old_state = States.TO_CONTROL;

                                }
                                else{
                                    String old_state_str = old_state_obj.toString();
                                    old_state = States.valueOf(old_state_str);
                                }
                            }

                            if (rootPid != null && model != null) {
                                Workflow wf = new Workflow();
                                WorkflowDoc wfdoc = new WorkflowDoc(
                                        decision_date,
                                        old_state,
                                        "excelImporter",
                                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")),
                                        model,
                                        false,//irrelevant
                                        LicensesAssignation.NONE
                                );
                                States newstate = wf.calculateCuratorTransition(rootPid, solrClient, wfdoc, desiredState);
                                //System.out.println("NewState: " + newstate.toString());
                                if(newstate != null)
                                    wf.executeTransition(wfdoc, wfdoc.user(), "importing from excel", doc, newstate);
                            }
                        } catch (Exception e) {
                            System.err.println("Caught exception in " + tsvFile.getName() +" of type: "+e.getClass()+ ": " + e.getMessage());
                            e.printStackTrace();
                        }
                        if(nameIDArray!=null) {
                            XmlProcessor.trySetLastAuthor(doc, Arrays.asList(nameIDArray));
                            List<String> authorJsons = new ArrayList<>();
                            Arrays.asList(nameIDArray).forEach(author -> {
                                authorJsons.add(author.toJsonString());
                                doc.addField(AppDataFields.AUTHORS_KRAMERIUS_FIELD, author.toJsonString());
                                if (author.getNameIdentifier() != null && !author.getNameIdentifier().isEmpty()) {
                                    doc.addField(AppDataFields.NAME_IDENTIFIER_FIELD, author.getNameIdentifier());
                                }
                            });
                            //for (String authorJson : authorJsons) {
                            /*if(authorJsons.size()>1){
                                //doc.getDocument().setField(AppDataFields.AUTHORS_KRAMERIUS_FIELD,"");
                                System.out.println(pid);
                            }*/
                            Map<String, Object> operation = new HashMap<>();
                            operation.put("add-distinct", authorJsons);

                            doc.getDocument().setField(AppDataFields.AUTHORS_KRAMERIUS_FIELD, operation);
                            //doc.getDocument().addField(AppDataFields.AUTHORS_KRAMERIUS_FIELD, authorJsons);
                        //}

                            //System.out.println(Arrays.toString(authorJsons.toArray()));

                            //System.out.println("Lenght: "+authorJsons.size()+ ". " + doc.getFieldValue(AppDataFields.AUTHORS_KRAMERIUS_FIELD));
                            /*if(authorJsons.size()>1){
                                //System.out.println(doc.getDocument().getField(AppDataFields.AUTHORS_KRAMERIUS_FIELD).toString());
                                for (String authorJson : authorJsons) {System.out.println("   "+authorJson);}
                            }*/

                            for (SolrInputDocument authorDoc : Arrays.stream(nameIDArray).map(AuthorData::toAuthoritySolrDocument).toList()) {
                                if (authorDoc != null)
                                    solrClient.add(Collection.AUTHORS_COLLECTION, authorDoc);
                            }
                        }
                        /*
                        if (doc.getFieldValue(AppDataFields.PID_FIELD) == doc.getFieldValue(AppDataFields.ROOT_PID_FIELD)) {
                            doc.setField(AppDataFields.ROOT_TITLE_FIELD, doc.getFieldValue(AppDataFields.TITLE_FIELD));
                        }*/
                        //doc.addField("state_user", "TableParser");
                        solrClient.add(Collection.TITLES_COLLECTION, doc.getDocument());
                    }
                } catch (ConnectException e) {
                    System.err.println("Kramerius connection error " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                } catch (Exception e) {
                    System.err.println("Critical Exception: in file:" + tsvFile.getName() + " of type:" + e.getClass()
                            + ": " + e.getMessage());
                    e.printStackTrace();
                    // sent_request = true;
                }
            }

            //solrClient.commit(Collection.TITLES_COLLECTION);
            //solrClient.commit(Collection.AUTHORS_COLLECTION);
            System.out.println("All TSV files indexed successfully.");
            System.exit(0);

        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } finally {
            if (solrClient != null) {
                try {
                    solrClient.commit(Constants.Collection.TITLES_COLLECTION);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                try {
                    solrClient.commit(Constants.Collection.AUTHORS_COLLECTION);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }
/*
    private static String getField(SolrInputDocument doc, String fieldName) {
        SolrInputField field = doc.getField(fieldName);
        return field != null ? (String) field.getValue() : null;
    }*/

    private static States parsePublicString(SolrInputDocumentWrapper doc, int recordNumber) {
        Object val = doc.getFieldValue("public_string");
        if (val != null) {
            final String value = val.toString();
            switch (value.toLowerCase()) {
                case "ano", "yes", "true" -> {
                    //doc.setField("public", true);
                    doc.addField(AppComputedFields.ASSIGNED_LICENSES, "public");
                    return States.CONTROLLED_PUBLIC;
                }
                case "ne", "no", "false" -> {
                    //doc.setField("public", false);
                    doc.addField(AppComputedFields.ASSIGNED_LICENSES, "onsite");
                    return States.CONTROLLED_PRIVATE;
                }
                default -> {
                    System.err.println("Unexpected boolean value on row: " + recordNumber + "\n\tVALUE: " + value);
                    doc.addField(AppComputedFields.LABELS_FIELD, Labels.EXCEL_INCORRECT_LICENSE);
                }
            }
        }
        return States.CONTROLLED_UNKNOWN;
    }
/*
    private static States setState(SolrInputDocumentWrapper doc,String pid) {
        //States val;
        if (doc.getFieldValue(ApplFields.ASSIGNED_LICENSES)!=null) {
            return;
        } else
        Object public_since_obj = doc.getFieldValue("public_since");
        if (public_since_obj != null) {
            int public_since;
            System.out.println("Public since field found");
            try {
                public_since = Integer.parseInt(public_since_obj.toString());
            } catch (NumberFormatException e) {
                return States.CONTROLLED_UNKNOWN;
            }
            if (public_since > LocalDate.now().getYear()) {
                val = States.NONCONTROLLED_PRIVATE; // KONTROLOVANÉ- NEVEŘEJNÉ
            } else {
                val = States.NONCONTROLLED_PUBLIC; // KONTROLOVANÉ - VOLNÉ
            }
        } else {
            val = States.CONTROLLED_UNKNOWN; // KONTROLOVANÉ - NEZNÁMÉ
        }

        //doc.setField(ApplFields.STATE_FIELD, val);

        JSONObject h = new JSONObject()
                .put("date", LocalDateTime.now().toString())
                .put("reason", "import from excel")
                .put("user", "importer")
                .put(AppComputedFields.STATE_FIELD, val.name());

        if (documentExists(pid)) {
            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("add", h.toString());
            doc.getDocument().addField(AppComputedFields.STATE_HISTORY_FIELD, partialUpdate);
        } else {
            doc.getDocument().setField(AppComputedFields.STATE_HISTORY_FIELD, h.toString());
        }
        return val;

    }*/

    private static boolean documentExists(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setRows(0);
        try {
            return solrClient.query(Collection.TITLES_COLLECTION, q).getResults().getNumFound() > 0;
        } catch (SolrServerException | IOException e) {
            System.err.println(e.getMessage());
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

    private static void ParseDates(SolrInputDocumentWrapper doc, String value, int recordNumber, boolean show_comma_info) {
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
            value = XmlProcessor.replaceWordsWithQMarks(doc, value, recordNumber, Originalvalue);

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

}
