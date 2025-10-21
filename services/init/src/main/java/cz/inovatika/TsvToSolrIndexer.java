package cz.inovatika;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

//TODO: Refactor !!
public class TsvToSolrIndexer {

    private static final String SOLR_URL = "http://localhost:8983/solr";
    private static String DIRECTORY_PATH = "../Data";  // change this 
    // private static final String DIRECTORY_PATH = "c:\\Users\\alber\\Projects\\KuratorskaAplikace\\tsv\\";  // change this

    static void add_year_range(String year_start, boolean confirmed_start, String year_end, boolean confirmed_end, SolrInputDocument doc) {

        try {
            int yearStart = Integer.parseInt(year_start);
            int yearEnd = Integer.parseInt(year_end);
            if (yearStart > yearEnd || yearStart < 1800 || yearEnd > 2025) {
                System.out.println("Suspicious year range: " + year_start + "-" + year_end);

            }
            doc.addField("date_range_start.year", yearStart);
            doc.addField("date_range_end.year", yearEnd);
            doc.addField("date.min", String.format("%04d-01-01T00:00:00Z", yearStart));
            doc.addField("date.max", String.format("%04d-12-31T00:00:00Z", yearEnd));
            //doc.addField("date_range_start.month", "1");
            //doc.addField("date_range_end.month", "12");
            doc.addField("confirmed_start_year", confirmed_start);
            doc.addField("confirmed_end_year", confirmed_end);
        } catch (NumberFormatException e) {
            System.err.println("Failed to parse year range: " + year_start + "-" + year_end);
        }
    }

    static void addyear(String year, SolrInputDocument doc, boolean confirmed) {
        add_year_range(year, confirmed, year, confirmed, doc);
    }

    static void add_year_since(String year, SolrInputDocument doc, boolean confirmed) {
        add_year_range(year, confirmed, "1999", false, doc);
    }

    static void add_year_before(String year, SolrInputDocument doc, boolean confirmed) {
        add_year_range("0", false, year, confirmed, doc);
    }

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
        try (SolrClient solrClient = new HttpJdkSolrClient.Builder(SOLR_URL).build()) {
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
                        String secondPid = null;
                        String[] authorArray;
                        AutorData[] nameIDArray = null;
                        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
                            String header = headers.get(entry.getKey());
                            if (header == null) {
                                continue;
                            }
                            String value = entry.getValue();
                            String Originalvalue = value;
                            try {
                                /*if("1926-".equals(value)){
                                    System.out.println("Debug");
                                }*/
                                switch (header) {
                                    case "authors" -> {
                                        authorArray = value.split("  |\\|");
                                        if (authorArray.length > 0 && !authorArray[0].isEmpty()) {
                                            doc.addField(header, authorArray);
                                        }
                                    }
                                    case "imported" -> {
                                        if (value != null && !value.isEmpty()) {
                                            String[] imported = value.split("\\.");
                                            doc.addField(header, String.format("%04d-%02d-%02dT00:00:00Z", Integer.valueOf(imported[2]), Integer.valueOf(imported[1]), Integer.valueOf(imported[0])));
                                        } else {
                                            System.err.println("Empty imported date on row: " + recordNumber);
                                        }
                                    }
                                    case "pid" -> {
                                        String[] parts = value.trim().replaceAll("\\|", " ").split("\\s+");
                                        for (int i = 0; i < parts.length; i++) {
                                            if(!parts[i].startsWith("uuid:")){
                                                parts[i] = "uuid:" + parts[i];
                                            }
                                        }
                                        pid = parts[0];
                                        if (parts.length > 1) {
                                            if (parts.length > 2) {
                                                System.out.println("More than two UUIDs found, using first two." + parts[2] + " Row: " + recordNumber);
                                            }
                                            secondPid = parts[1];
                                            //System.out.println("Multiple UUIDs found, First one: " + uuid + " Second one:" + secondPid + " Row: " + recordNumber);
                                        } 
                                        doc.addField(header, pid);
                                        
                                        // NDK. K5
                                        Document xml = getBiblioMods(client, builder, pid, "https://ndk.cz/search/api/v5.0/item/" + pid + "/streams/BIBLIO_MODS");
                                        if (xml == null) {
                                            // MZK. K7
                                            // https://api.kramerius.mzk.cz/search/api/client/v7.0/items/uuid:4f0d2790-37d6-11e6-8746-005056825209/metadata/mods
                                            xml = getBiblioMods(client, builder, pid, "https://api.kramerius.mzk.cz/search/api/client/v7.0/items/" + pid + "/metadata/mods");
                                        }
                                        nameIDArray = XmlProcessor.processBiblioMods(xml, doc);
                                    }
                                    case "year_of_death_last_author", "public_since" -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4) {
                                            doc.addField(header, value);
                                        } else {
                                            System.err.println("Unexpected year value on row: " + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                        }
                                    }
                                    default ->
                                        doc.addField(header, value);
                                }

                            } catch (IndexOutOfBoundsException e) {
                                System.err.println("Caught OOBexception in" + tsvFile.getName() + ": " + e.getMessage() + "\n\tVALUE: " + Originalvalue);
                            }
                        }
                        parsePublicString(doc, recordNumber);
                        ParseDates(doc, getField(doc, "date.str"), recordNumber, show_comma_info);
                        trySetLastAuthor(doc, nameIDArray, getField(doc, "last_author"));
                        extractAuthorData(show_comma_info, solrClient, doc, recordNumber, nameIDArray);

                        solrClient.add("titles", doc);
                        if (secondPid != null) {
                            SolrInputDocument seconddoc = doc.deepCopy();
                            seconddoc.setField("pid", secondPid);
                            solrClient.add("titles", seconddoc);
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Critical Exception: in file:" + tsvFile.getName() + "of type:" + e.getClass() + ": " + e.getMessage());
                    //sent_request = true;
                }
            }

            solrClient.commit("titles");
            solrClient.commit("authors");
            System.out.println("All TSV files indexed successfully.");

        } catch (ConnectException e) {
            System.err.println("Failed to connect to Solr " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error indexing data: Exception type: " + e.getClass().getName() + " Message:" + e.getMessage());
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
            case "ano", "yes", "true" ->
                doc.addField(header, true);
            case "ne", "no", "false" ->
                doc.addField(header, false);
            default -> {
                System.err.println("Unexpected boolean value on row: " + recordNumber + "\n\tVALUE: " + value);
            }
        }
    }

    private static void extractAuthorData(final boolean show_comma_info, final SolrClient solrClient, SolrInputDocument doc,
            final int recordNumber,final AutorData[] nameIDArray) throws SolrServerException, IOException {
        if (nameIDArray == null) {
            return;
        }
        for (AutorData autor : nameIDArray) {
            doc.addField("authors_kramerius", autor.toJsonString());
            if (autor.nameIdentifier != null && !autor.nameIdentifier.isEmpty()) {
                doc.addField("nameIdentifier", autor.nameIdentifier);
            }

            SolrInputDocument aDoc = new SolrInputDocument();
            aDoc.addField("nameIdentifier", autor.nameIdentifier);
            if (autor.nameIdentifier != null) {
                aDoc.addField("id", autor.nameIdentifier);
            } else {
                aDoc.addField("id", normalize(autor.full + autor.date));
            }
            aDoc.addField("full", autor.full);
            aDoc.addField("family", autor.family);
            aDoc.addField("given", autor.given);
            //aDoc.addField("date", autor.date);
            aDoc.addField("termsOfAddress", autor.termsOfAddress);
            //aDoc.addField("birth", autor.family);
            // aDoc.addField("death", autor.family);

            ParseDates(aDoc, autor.date, recordNumber, show_comma_info);

            solrClient.add("authors", aDoc);
        }

    }

    private static void trySetLastAuthor(SolrInputDocument doc, AutorData[] nameIDArray, String last_author) {
        //System.out.println("Processing last_author: '" + last_author + "'");
        try {
            if (last_author != null && (nameIDArray != null)) {
                for (AutorData autor : nameIDArray) {
                    //System.out.println("Comparing last_author: '" + last_author + "' with author: '" + authorArray[i] + "' having nameID: '" + nameIDArray[i] + "'");
                    // if (last_author.contains(authorArray[i])) {
                    //      doc.addField("last_author_nameIdentifier", nameIDArray[i].nameIdentifier);
                    //   }
                    if (similartyMatch(normalize(last_author), normalize(autor.full), .5)) {
                        autor.isLast = true;
                        doc.setField("last_author_nameIdentifier", autor.nameIdentifier);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Critical Exception occured in:last_author_nameIdentifier: " + e.getMessage());
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

    private static void dateFormatError(int recordNumber, String Originalvalue, String value) {
        System.err.println("Unexpected date format on row: " + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
    }

    private static void dateFormatError(int recordNumber, String Originalvalue, String value, String message) {
        System.err.println(message + " on row: " + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
    }

    private static Document getBiblioMods(HttpClient client, DocumentBuilder builder, String pid, String url) throws IOException, InterruptedException {
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
            System.out.println("Failed to fetch MODS for second pid " + pid + ", response code: " + (response != null ? response.statusCode() : "null response"));
            return null;
        }
    }

    private static boolean ParseDates(SolrInputDocument doc, String value, int recordNumber, boolean show_comma_info) {
        try {
            String Originalvalue = value;
            /*if("[191-?]".equals(value)){
            System.out.println("Debug");
            }*/
            if (value == null || value.isEmpty()) {
                System.err.println("Empty date on row: " + recordNumber);
                return true;
            }
            if (value.contains("asi")) {
                String year = value.replaceAll("[^0-9]", "");
                if (year.length() == 4) {
                    value = year + "?";
                } else {
                    dateFormatError(recordNumber, Originalvalue, value);
                }
            } else if (value.contains("okolo r. 1900")) {
                value = "1900?";
            } else if (value.contains("chybně")) {
                value = value.split(",")[0].trim();
            } else if (value.contains("mezi")) {
                value = value.replace("mezi r.", "mezi");
                String[] parts = value.split(" ");
                switch (parts.length) {
                    case 2 ->
                        value = parts[1];
                    case 4 ->
                        value = parts[1] + "-" + parts[3];
                    default ->
                        dateFormatError(recordNumber, Originalvalue, value);
                }
            } else if (value.contains("po")) {
                value = value.replace("po r.", "po ");
                value = value.replaceAll("[\\[\\]]", "");
                String[] parts = Arrays.stream(value.split(" "))
                        .filter(s -> !s.isEmpty())
                        .toArray(String[]::new);
                if (parts.length != 2) {
                    dateFormatError(recordNumber, Originalvalue, value);

                } else {
                    String year = parts[1];
                    if (year.length() == 4) {
                        add_year_since(year, doc, true);
                    } else {
                        add_year_since(parts[1].substring(0, 4), doc, false);
                    }
                }
                return true;
            } else if (value.contains("před")) {
                value = value.replace("před r.", "před");
                value = value.replaceAll("[\\[\\]]", "");
                String[] parts = value.split(" ");
                if (parts.length != 2) {
                    dateFormatError(recordNumber, Originalvalue, value);

                } else {
                    String year = parts[1];
                    if (year.length() == 4) {
                        add_year_before(year, doc, true);
                    } else {
                        add_year_before(parts[1].substring(0, 4), doc, false);
                    }
                }
                return true;
            } else if (value.contains("nebo")) {
                if (!value.contains(" nebo")) {
                    value = value.replace("nebo", " nebo");
                }
                if (!value.contains("nebo ")) {
                    value = value.replace("nebo", "nebo ");
                }
                String[] parts = value.split(" ");
                if (parts.length != 3) {
                    dateFormatError(recordNumber, Originalvalue, value);
                } else {
                    value = parts[0] + "?-" + parts[2] + "?";
                }
            }
            if (value.contains(",")) {
                String og = value;
                value = value.split(",")[0].trim();
                if (show_comma_info) {
                    System.out.println("Comma found in " + og + ", using first part: " + value + " Row: " + recordNumber);
                }
            }
            if (value.contains("19--") && value.length() <= 6) {// Handle 19-- format with or without []
                value = "1900?-1999?";
            } else if (value.contains("19-") && value.length() <= 5) {// Handle 19- format with or without []
                value = "1900?-1999?";
            }
            value = value.replaceAll("19(\\d)-", "19$10?-19$19?");
            if (value.length() == 5) {//1999] or [1999
                if (value.endsWith("]")) {
                    value = value.substring(0, 4) + '-' + value;
                } else if (value.startsWith("[")) {
                    value = value + '-' + value.substring(1);
                }
            }
            value = value.replaceAll("\\[?19--\\??\\]?", "19");
            value = value.replaceAll("\\s+až\\s+", "-");//replace až with dash
            String kolem_fix = value.replaceAll("kolem r.\\s+", "?");//replace kolem with ?
            value = value.equals(kolem_fix) ? value : kolem_fix + "?";
            value = value.replaceAll("\\s+a\\s+", "-");//replace a with dash
            value = value.replaceAll("ca\\.?\\s+", "?");//replace ca with ?
            //value = value.replaceAll("[\\[\\]]", "");//Just purge the brackets.
            String[] parts = value.split("-");
            switch (parts.length) {
                case 3 -> {
                    String yearStart = parts[0].trim();
                    String yearEnd = parts[2].trim();
                    if (yearEnd.length() == 2 || yearEnd.length() == 3) {
                        yearEnd = "19" + yearEnd; // Assuming 20st century for 2-digit years
                    }
                    add_year_range(
                            yearStart.length() == 4 ? yearStart : yearStart.substring(1),
                            yearStart.length() == 4,
                            yearEnd.length() == 4 ? yearEnd : yearEnd.substring(0, 4),
                            yearEnd.length() == 4, doc);
                }
                case 2 -> {
                    String yearStart = parts[0].trim();
                    String yearEnd = parts[1].trim();
                    boolean confirmedStart = yearStart.length() == 4;
                    boolean confirmedEnd = yearEnd.length() == 4;
                    if (yearStart.contains("[") || yearStart.contains("?")) {
                        confirmedStart = false;
                    }
                    if (yearEnd.contains("]") || yearEnd.contains("?")) {
                        confirmedEnd = false;
                    }
                    if (yearEnd.isEmpty()) {
                        yearEnd = "1999";
                        confirmedEnd = false;
                    }

                    yearStart = yearStart.replaceAll("[\\[\\]\\?]", "");
                    yearEnd = yearEnd.replaceAll("[\\[\\]\\?]", "");

                    if ("?".equals(yearStart)) {
                        yearStart = "1900";
                    }
                    if ("?".equals(yearEnd)) {
                        yearEnd = "1999";
                    }
                    if (yearStart.length() < 4) {
                        confirmedStart = false;
                        while (yearStart.length() < 4) {
                            yearStart += "0";
                        }
                        //System.err.println("Start year or End year are too short " + tsvFile.getName() + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                    }
                    if (yearEnd.length() < 4) {
                        if ("19".equals(yearEnd)) {
                            confirmedEnd = false;
                            yearEnd = "1999";
                        } else {
                            confirmedEnd = true;
                            yearEnd = "19" + yearEnd;
                        }
                    }

                    add_year_range(
                            yearStart,
                            confirmedStart,
                            yearEnd,
                            confirmedEnd, doc);

                }
                case 1 -> {
                    String year = parts[0].trim();
                    boolean confirmed = year.length() == 4;
                    if (year.length() == 5) {//year? or ?year
                        if (year.contains("?")) {
                            if (year.indexOf('?') == 0) {
                                year = year.substring(1, 5);
                            } else {
                                year = year.substring(0, 4);
                            }

                        } else if (year.startsWith("c")) {
                            year = year.substring(1, 5);
                            confirmed = true;
                        } else {
                            dateFormatError(recordNumber, Originalvalue, value);
                        }
                    } else if (year.length() == 6 || value.length() == 7) {
                        year = year.substring(1, 5);
                    } else if (year.length() == 2) {
                        year = year + "00";
                    }
                    if (NumberUtils.isCreatable(year)) {
                        if (value.contains(year + "-")) {
                            add_year_since(year, doc, confirmed);
                        } else {
                            addyear(year, doc, confirmed);
                        }
                    } else {
                        dateFormatError(recordNumber, Originalvalue, value, "Failed to parse date");
                    }
                }
                default ->
                    dateFormatError(recordNumber, Originalvalue, value);
            }

            return false;
        } catch (Exception e) {
            System.out.println("Critical Exception occured in:date_str: " + e.getMessage());
            return false;
        }
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }
        String result = input.toLowerCase(Locale.ROOT);
        result = Normalizer.normalize(result, Normalizer.Form.NFD);
        result = result.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        result = result.replaceAll("[^a-z0-9]", "");
        return result;
    }

    public static boolean similartyMatch(String catalogNormalizedTitle, String distriNormalizedTitle, double shoda) {
        LevenshteinDistance dist = LevenshteinDistance.getDefaultInstance();
        Integer calculatedDistance = dist.apply(catalogNormalizedTitle, distriNormalizedTitle);
        double maxDistance = Math.round((1 - shoda) * ((double) distriNormalizedTitle.length()));

        return maxDistance >= calculatedDistance;
    }
}
