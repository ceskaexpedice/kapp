package cz.inovatika;

import cz.inovatika.Constants.Collection;
import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.Labels;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.solr.SolrInputDocumentWrapper;
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
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import java.io.*;
import java.net.ConnectException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields.LAST_AUTHOR_EXCEL_FIELD;

//TODO: Refactor !!
public class TsvToSolrIndexer {

    private static final String SOLR_URL = "http://localhost:8983/solr";//"http://solr-kappScheduler:8983/solr";//

    private static String DIRECTORY_PATH = System.getProperty("user.home") + "/.kapp/Data";//"../Data"; // change this
    private static final String TSV_MAPPINGS = System.getProperty("user.home") + "/.kapp/tsv-mappings.json";//"services/init/src/main/resources/tsv-mappings.json"
    private final static SolrClient solrClient = new Http2SolrClient.Builder(SOLR_URL).withConnectionTimeout(100, TimeUnit.SECONDS).build();
    static ApplicationConfig applicationConfig;

    //private final static String MZKLink = "https://api.kramerius.mzk.cz/search/api/client/v7.0/";
    private static String current_table = "";

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length > 0) {
            DIRECTORY_PATH = args[0];
        }
        InputStream resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
        applicationConfig = ApplicationConfig.loadFromStream(resourceAsStream);
        /*DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        HttpClient client = HttpClient.newHttpClient();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        DocumentBuilder builder = factory.newDocumentBuilder();*/

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
            Set<String> missing_headers = new HashSet<>();
            for (File tsvFile : tsvFiles) {
                current_table =  tsvFile.getName();
                System.out.println("Processing: " + tsvFile.getName());
                SolrInputDocument docerr = null;
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

                        int recordNumber = (int) record.getRecordNumber() + 1; // +1 to account for header
                        String pid = null;
                        String rootPid = null;
                        String public_string = null;
                        ArrayList<String> notes = new ArrayList<>();
                        ArrayList<String> labels = new ArrayList<>();
                        labels.add(Labels.EXCEL);
                        ArrayList<String> users = new ArrayList<>();
                        //users.add("TableParser");
                        //String model = null;
                        //String[] authorArray;
                        //AuthorData[] nameIDArray = null;
                        Integer year_of_death_last_author = null;
                        Integer date_issued = null;
                        Integer public_year = null;
                        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
                            String header = headers.get(entry.getKey());
                            if (header == null) {
                                if(!missing_headers.contains(entry.getKey())){
                                    System.out.println("Missing header: " + entry.getKey());
                                    missing_headers.add(entry.getKey());
                                }
                                continue;
                            }
                            else header = header.toLowerCase().trim();
                            String value = entry.getValue();
                            //String Originalvalue = value;
                            try {
                                switch (header) {
                                    case "public_string"->public_string = value;
                                    case "note"-> {if(!value.isEmpty()) notes.add(value);}
                                    /*case "authors" -> {
                                        authorArray = value.split(" {2}|\\|");
                                        if (authorArray.length > 0 && !authorArray[0].isEmpty()) {
                                            doc.addField(header, authorArray);
                                        }
                                    }*/
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
                                        }

                                        doc.setField(header, pid);
                                        //doc.setField(KramFields.ROOT_PID_FIELD, rootPid);
                                        /*
                                        // NDK. K5
                                        Document xml = getBiblioMods(client, builder, pid, "https://ndk.cz/search/api/v5.0/item/" + pid + "/streams/BIBLIO_MODS");
                                        if (xml == null) {
                                            // MZK. K7
                                            // https://api.kramerius.mzk.cz/search/api/client/v7.0/items/uuid:4f0d2790-37d6-11e6-8746-005056825209/metadata/mods
                                            xml = getBiblioMods(client, builder, pid, MZKLink + "items/" + pid + "/metadata/mods");
                                        }

                                        //nameIDArray =  XmlProcessor.processBiblioMods(xml, doc);
                                        if (xml != null)
                                            nameIDArray = AuthorDataUtils.parseAuthorData(xml).toArray(AuthorData[]::new);

                                        // String query = "q=pid:" + URLEncoder.encode(
                                        // ClientUtils.escapeQueryChars(pid), "UTF-8") ;
                                        // JSONObject item = getItem(client, builder, pid,
                                        // "https://ndk.cz/search/api/v5.0/search?" + query);

                                        JSONObject item = getItem(client, pid, "https://ndk.cz/search/api/v5.0/item/" + pid);
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
                                        }*/
                                    }
                                    case "year_of_death_last_author"/*, "public_since"*/ -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4) {
                                            year_of_death_last_author = Integer.valueOf(value);
                                            doc.setField(AppComputedFields.LAST_YEAR_DEATH, value);
                                        } /*else {
                                             if(!"?".equals(value))System.err.println("Unexpected year value on row: "
                                             + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ?
                                             Originalvalue : Originalvalue + " _ " + value));
                                        }*/
                                    }
                                    case "public_since" -> {
                                        if (NumberUtils.isCreatable(value) && value.length() == 4)
                                        {
                                            public_year = Integer.valueOf(value);
                                        }
                                    }
                                    case "date_issued_str" -> {
                                        doc.setField(AppComputedFields.DATE_ISSUED_STRING, value);
                                        ParseDates(doc, value, recordNumber, show_comma_info,labels,notes);
                                        Object strobj = doc.getFieldValue(AppComputedFields.DATE_ISSUED_YEAR);
                                        if (strobj != null) {
                                        String cleanedString = strobj.toString();
                                        cleanedString = cleanedString.replaceAll("[^0-9]", "");
                                        if (cleanedString.length() == 4) date_issued = Integer.valueOf(cleanedString);
                                        //System.out.println(date_issued);// + " " + year_of_death_last_author);
                                        }
                                    }
                                    case "checked_by" ->{
                                        if (!value.contains(".")) {//Some users put dates here
                                        //System.out.println("Checking by: " + value);
                                            users.addAll(Arrays.asList(value.split("/")));
                                        }

                                    }
                                    case "checked_by_override"->{
                                        if (!value.isEmpty() && users.isEmpty()){
                                            //System.out.println("Overriding with: " + value);
                                            users.addAll(Arrays.asList(value.split("/")));
                                        }
                                    }
                                    case "last_author"->{
                                        if (value.isEmpty())continue;
                                        notes.add(entry.getKey() + " : " +  value);
                                        switch (value.toLowerCase()
                                                .replace(" (mapa)","")
                                                .replace(" (text)","")
                                                .replace("anonzm","anonym")
                                                .replace(" - zeměměřičský úřad čechy a morava","")
                                                .replace(" dílo","").trim()){
                                            case "kolektivní","vypracoval kolektiv","řada autorů"->
                                                    doc.setField("is_collective",true);
                                            case "anonzm","anonym","anonymní"->
                                                    doc.setField("is_anonymous",true);
                                            case "úřední","úřední dokument"->
                                                    doc.setField("is_official",true);
                                            case "pseudonymní"->{
                                                doc.setField("is_pseudonymous",true);
                                            }
                                            case "kolektivní-anonymní","anonymní, kolektivní","anonymní/kolektivní","kolektivní/anonymní","kolektivní/anonymni","kolektivní/anonymní edice","anonymní (kolektivní)"->{
                                                doc.setField("is_anonymous",true);
                                                doc.setField("is_collective",true);
                                            }
                                            case "úúřední dokument/anonym","úřední + anonymní sestavovatel","úřední, anonym","úřední/anonym","anonym/úřední"->{
                                                doc.setField("is_anonymous",true);
                                                doc.setField("is_official",true);
                                            }
                                            case "úřední/kolektivní"->{
                                                doc.setField("is_official",true);
                                                doc.setField("is_collective",true);
                                            }
                                            case "anonymní/pseudonymní"->{
                                                doc.setField("is_anonymous",true);
                                                doc.setField("is_pseudonymous",true);
                                            }
                                            default ->{
                                                String val = value.toLowerCase();
                                                if (val.contains("anonym")||val.contains("pseudo")||val.contains("kolektivní")||val.contains("úřední")){
                                                    if (val.contains("anonymní dílo")){doc.setField("is_anonymous",true);}
                                                    if(val.contains("úřední")){doc.setField("is_official",true);}
                                                    if(val.contains("kolektivní")){doc.setField("is_collective",true);}
                                                    //if(value.toLowerCase().contains("pseudo")){doc.setField("is_pseudonymous",true);}
                                                    System.out.println("Labeled: " + value);
                                                    labels.add("Nejistá anonymita/úřednost/kolektivnost");}
                                                else {
                                                    List<String> out = LastAuthorExcelParser.parse(value);
                                                    if (!out.isEmpty()) {
                                                        doc.setField(LAST_AUTHOR_EXCEL_FIELD,out);
                                                        //System.out.println("AUTHORS: " + out.toString());
                                                    }
                                                    else {
                                                        labels.add("Chyba v posledním autorovi");
                                                        //System.out.println("Unknown: " + value);
                                                    }
                                                }
                                            }
                                        }


                                    }
                                    case "title"->{
                                        doc.setField("title",value);//This may change the title that came from kramerius, but it is not going to appear in kramerius anyway, and there are books that are in Excel only that need title
                                    }
                                    case "authors","id_ccnb"->{
                                        //skip
                                    }
                                    case "physical_locations"->{
                                        doc.addField("archiver_location",value);
                                    }
                                    default -> {
                                        if (!value.isEmpty()) notes.add(entry.getKey() + " : " +  value);
                                        //System.out.println("Unknown header: " + header);
                                        //doc.addField(header, value);
                                    }
                                }

                            } catch (IndexOutOfBoundsException e) {
                                System.err.println("Caught OutOfBoundsException in" + tsvFile.getName() + ": " + e.getMessage()
                                        + "\n\tVALUE: " + /*Original*/value);
                            }
                        }
                        if (pid == null|| pid.trim().isEmpty() ||pid.trim().equals("uuid:"))continue;
                        /*if (Objects.equals(pid, "uuid:da9ae0b0-4c5f-11ec-9253-5ef3fc9bb22f") || Objects.equals(pid, "da9ae0b0-4c5f-11ec-9253-5ef3fc9bb22f")){
                            System.out.println("DEBUG");
                        }*/
                        if (public_year !=null) {
                            if (year_of_death_last_author != null ) {
                                int public_year_difference = year_of_death_last_author + applicationConfig.workflow().getAuthorDeadEnoughYear() - public_year;
                                if (Math.abs(public_year_difference)>1 && public_year_difference!=applicationConfig.workflow().getAuthorDeadEnoughYear()){
                                    notes.add("Public year in excel: " + public_year);
                                    labels.add("Excel has unexpected year of publication");
                                }
                            } else if(date_issued!=null){
                                int public_year_difference = date_issued +applicationConfig.workflow().getAuthorDeadEnoughYear() - public_year;
                                if (Math.abs(public_year_difference)>1 && public_year_difference!=applicationConfig.workflow().getAuthorDeadEnoughYear()){
                                    notes.add("Public year in excel: " + public_year);
                                    labels.add("Excel has unexpected year of publication");
                                }
                            }
                        }
                        //if(pid.equals("uuid:b5d91e40-a260-11ef-8fc3-5ef3fc9bb22f")) System.out.println("DEBUG");
                        States desiredState = parsePublicString(/*doc,*/ recordNumber,public_string,labels,notes);
                        Map<String,Object> labelsUpdateMap = new HashMap<>();
                        //labelsUpdateMap.put("remove", getRemoves());//Purges the table of broken values
                        /*if(labels.size()>1){
                            System.out.println(labels);
                        }*/
                        labelsUpdateMap.put("add-distinct", labels);
                        doc.getDocument().addField(AppComputedFields.LABELS_FIELD,labelsUpdateMap);
                        Map<String,Object> notesUpdateMap = new HashMap<>();
                        notesUpdateMap.put("add-distinct", notes);
                        doc.getDocument().addField("note",notesUpdateMap);


                        //if (desiredState == States.CONTROLLED_UNKNOWN) desiredState = setState(doc,pid);
                       // if (desiredState != States.TO_CONTROL) System.out.println(desiredState+" "+pid);
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
                            String model = getModelFromSolr(pid);
                            if (rootPid != null && model != null) {
                                WorkflowDoc wfdoc = new WorkflowDoc(
                                        date_issued,
                                        year_of_death_last_author,
                                        old_state,
                                        "excelImporter",
                                        model,
                                        false//,//irrelevant
                                        //old_state.getLicensesAssignation()
                                );
                                States new_state = Workflow.calculateCuratorTransition(rootPid, solrClient, wfdoc, desiredState);
                                //System.out.println("NewState: " + new_state.toString());
                                if(new_state != null)
                                    Workflow.executeTransition(wfdoc, wfdoc.user(), "importing from excel", doc, new_state, applicationConfig.workflow());
                            }
                        } catch (Exception e) {
                            System.err.println("Caught exception in " + tsvFile.getName() +" of type: "+e.getClass()+ ": " + e.getMessage());
                            e.printStackTrace();
                        }/*
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
                            }
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
                            }

                            for (SolrInputDocument authorDoc : Arrays.stream(nameIDArray).map(AuthorData::toAuthoritySolrDocument).toList()) {
                                if (authorDoc != null)
                                    solrClient.add(Collection.AUTHORS_COLLECTION, authorDoc);
                            }
                        }*/
                        /*
                        if (doc.getFieldValue(AppDataFields.PID_FIELD) == doc.getFieldValue(AppDataFields.ROOT_PID_FIELD)) {
                            doc.setField(AppDataFields.ROOT_TITLE_FIELD, doc.getFieldValue(AppDataFields.TITLE_FIELD));
                        }*/
                        Map<String,Object> userUpdateMap = new HashMap<>();
                        userUpdateMap.put("add-distinct",users );
                        doc.getDocument().setField("state_user",(users.isEmpty())?"Excel without curator":"Excel");//userUpdateMap);
                        doc.getDocument().addField("modified_by_user", userUpdateMap);

                        docerr = doc.getDocument();
                        solrClient.add(Collection.TITLES_COLLECTION, doc.getDocument());
                    }
                } catch (ConnectException e) {
                    System.err.println("Kramerius connection error " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
                } catch (Exception e) {
                    if(docerr!=null) System.err.println(docerr);
                    System.err.println("Critical Exception: in file:" + tsvFile.getName() + " of type:" + e.getClass()
                            + ": " + e.getMessage());
                    e.printStackTrace();
                    System.exit(1);
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
                    solrClient.commit(Collection.TITLES_COLLECTION);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
                try {
                    solrClient.commit(Collection.AUTHORS_COLLECTION);
                } catch (Exception e) {
                    System.err.println(e.getMessage());
                }
            }
        }
    }

    private static List<String> getRemoves() {
        List<String> removes = new ArrayList<>();
        removes.add("{add-distinct=[excel]}");
        //removes.add("{add-distinct=excel}");
        removes.add("{add-distinct=excel_incorrect_license}");
        removes.add("{add-distinct=excel_failed_to_parse_date}");
        removes.add("{add-distinct=excel_suspicious_year_range}");
        //removes.add("{add-distinct=[excel_incorrect_license, excel]}");
        removes.add("{add-distinct=[excel, excel_incorrect_license]}");
        return removes;
    }
/*
    private static String getField(SolrInputDocument doc, String fieldName) {
        SolrInputField field = doc.getField(fieldName);
        return field != null ? (String) field.getValue() : null;
    }*/

    private static States parsePublicString(/*SolrInputDocumentWrapper doc,*/ int recordNumber,String value,ArrayList<String> labels,ArrayList<String> notes) {
        //Object val = doc.getFieldValue("public_string");
        if (value != null) {
            //final String value = val.toString();
            switch (value.trim().toLowerCase()) {
                case "ano", "yes", "true" -> {
                    //doc.setField("public", true);
                    //doc.getDocument().addField(AppComputedFields.ASSIGNED_LICENSES, "public");
                    //Map<String, Object> operation = new HashMap<>();
                    //operation.put("add-distinct", "public");
                    //doc.getDocument().setField(AppComputedFields.ASSIGNED_LICENSES, operation);
                    return States.CONTROLLED_PUBLIC;
                }
                case "ne", "no", "false" -> {
                    //doc.setField("public", false);
                    //doc.getDocument().addField(AppComputedFields.ASSIGNED_LICENSES, "onsite");
                    return States.CONTROLLED_PRIVATE;
                }
                case "?" ->{
                    return States.CONTROLLED_UNKNOWN;
                }
                case ""->{
                    return States.TO_CONTROL;
                }
                default -> {
                    notes.add("Nečekaná hodnota zveřejnění v "+current_table+" na řádku: " + recordNumber + "Hodnota: " + value);
                    //Map<String,Object> operation = new HashMap<>();
                    //operation.put("add-distinct", Labels.EXCEL);
                    //doc.getDocument().addField(AppComputedFields.LABELS_FIELD,operation);
                    //operation.put("add-distinct", Labels.EXCEL_INCORRECT_LICENSE);
                    //doc.getDocument().addField(AppComputedFields.LABELS_FIELD,operation);
                    labels.add(Labels.EXCEL_INCORRECT_LICENSE);
                    return States.CONTROLLED_UNKNOWN;
                }
            }
        }
        else
            return States.TO_CONTROL;
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
/*
    private static boolean documentExists(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setRows(1);
        try {
            return solrClient.query(Collection.TITLES_COLLECTION, q).getResults().getNumFound() > 0;
        } catch (SolrServerException | IOException e) {
            System.err.println(e.getMessage());
            return false;
        }
    }*/
    private static String getModelFromSolr(String pid) {
        SolrQuery q = new SolrQuery()
                .setQuery("pid:\"" + pid + "\"")
                .setRows(1);
        try {
            var results = solrClient.query(Collection.TITLES_COLLECTION, q).getResults();
            if (results.getNumFound() > 0)
            {
                Object modelobj = results.getFirst().getFieldValue("model");
                return modelobj!=null?modelobj.toString():null;
            }
            else
                return null;
        } catch (SolrServerException | IOException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }
/*
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
    }*/
/*
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
            System.err.println("Failed to fetch ITEM for root pid " + pid + ", response code: "
                    + (response != null ? response.statusCode() : "null response"));
            return null;
        }
    }*/
/*
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
    }*/

    private static void ParseDates(SolrInputDocumentWrapper doc, String value, int recordNumber, boolean show_comma_info,List<String> labels,List<String> notes) {
        try {
            String Originalvalue = value;
            /*
             * if("[191-?]".equals(value)){
             * System.out.println("Debug");
             * }
             */
            if (value == null || value.isEmpty()) {
                System.err.println("Empty date on row: "+current_table+" na řádku: " + recordNumber);
                return;
            }
            value = XmlProcessor.replaceWordsWithQMarks(value, current_table+" na řádku: " + recordNumber, Originalvalue,labels,notes);

            if (value.contains(",")) {
                String og = value;
                value = value.split(",")[0].trim();
                if (show_comma_info) {
                    System.out.println("Comma found in " + og + ", using first part: " + value + " V tabulce: " +current_table+" na řádku: " + recordNumber);
                }
            }
            value = XmlProcessor.normalizeImpreciseIssuedDateHint(value, labels);
            if (value.length() == 5) {// 1999] or [1999
                if (value.endsWith("]")) {
                    value = value.substring(0, 4) + '-' + value;
                } else if (value.startsWith("[")) {
                    value = value + '-' + value.substring(1);
                }
            }
            value = value.replaceAll("\\s+až\\s+", "-");// replace až with dash
            String kolem_fix = value.replaceAll("kolem r.\\s+", "?");// replace kolem with ?
            value = value.equals(kolem_fix) ? value : kolem_fix + "?";
            value = value.replaceAll("\\s+a\\s+", "-");// replace a with dash
            // value = value.replaceAll("[\\[\\]]", "");//Just purge the brackets.
            XmlProcessor.parseDateBasic(doc, value, current_table+" na řádku: " + recordNumber, Originalvalue,labels,notes);

        } catch (Exception e) {
            System.out.println("Critical Exception occurred in:date_str: " + e.getMessage());
        }
    }
    public static String removeParenthesesContent(String input) {
        if (input == null) return null;

        StringBuilder result = new StringBuilder();
        int depth = 0;

        for (char c : input.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (depth > 0) depth--;
            } else if (depth == 0) {
                result.append(c);
            }
        }

        return result.toString();
    }
    public static boolean containsDigit(String input) {
        if (input == null) return false;

        for (char c : input.toCharArray()) {
            if (Character.isDigit(c)) {
                return true;
            }
        }

        return false;
    }


}
