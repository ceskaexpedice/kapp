
import org.apache.commons.csv.*;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.solr.client.solrj.*;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;

import java.io.*;
import java.util.*;

public class TsvToSolrIndexer {

    private static final String SOLR_URL = "http://localhost:8983/solr/testing";
    private static final String DIRECTORY_PATH = "..\\Data";  // change this

    
    static void add_year_range(String year_start, boolean confirmed_start, String year_end, boolean confirmed_end, SolrInputDocument doc) {
        
        try{
            int yearStart =Integer.parseInt(year_start);
            int yearEnd =Integer.parseInt(year_end);
        if(yearStart > yearEnd || yearStart<1800 || yearEnd > 2025){
                System.out.println("Suspicious year range: " + year_start + "-" + year_end);
    
        }
            doc.addField("date_range_start.year", yearStart);
            doc.addField("date_range_end.year", yearEnd);
            doc.addField("date.min", String.format("%04d-01-01T00:00:00Z", yearStart));
            doc.addField("date.max", String.format("%04d-12-31T00:00:00Z", yearEnd));
            //doc.addField("date_range_start.month", "1");
            //doc.addField("date_range_end.month", "12");
            doc.addField("confirmed_start_year", confirmed_start);
            doc.addField("confirmed_end_year", confirmed_end);}
            catch (NumberFormatException e){
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
        add_year_range( "0", false,year, confirmed, doc);
    }

    public static void main(String[] args) {
        boolean show_comma_info = false;
        File folder = new File(DIRECTORY_PATH);
        if (!folder.isDirectory()) {
            System.err.println("Invalid directory: " + DIRECTORY_PATH);
            return;
        }

        try (SolrClient solrClient = new HttpSolrClient.Builder(SOLR_URL).build()) {
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
                    HashMap<String, String> headers = new HashMap<>() {
                        {
                            switch (tsvFile.getName()) {
                                case "2025 - Seznam přírůstků NDK pro kontrolu AP - NK_MZK.xlsx - do 1975.tsv",
                                "2025 - Seznam přírůstků NDK pro kontrolu AP - NK_MZK.xlsx - 19 stol..tsv" -> {
                                    put("Název", "root.title");
                                    put("Autor", "authors");
                                    put("Producent balíčku", "physical_locations.facet");
                                    put("Identifikátor ČNB", "id_ccnb");
                                    put("Datum vydání", "date.str");
                                    put("Identifikátor UUID", "pid");
                                    put("Datum importu", "imported");
                                    put("Rok úmrtí (nebo jiný rozhodný)", "year_of_death_last_author");
                                    put("Veřejné od", "public_since");
                                    put("Kontroloval", "checked_by");
                                    put("Zdroj", "source");
                                    put("Kontrola NK:", "check_NK");
                                    put("Kontrola NK:", "check_MZK");
                                    put("Poznámka", "note");

                                }
                                case "kontrola_MONOGRAFIE_1900-1975.xlsx - Veřejná periodika po roce 1920.tsv" -> {
                                    put("Název", "root.title");
                                    put("Vydáváno", "date.str");
                                    put("UUID", "pid");
                                    put("Ke zveřejnění", "public_since");
                                    put("Producent balíčku", "physical_locations.facet");
                                    put("Kontroloval", "checked_by");
                                    put("Zveřejnit", "public");
                                    put("Poznámky", "note");
                                }
                                default -> {
                                    if(tsvFile.getName().startsWith("kontrola_MONOGRAFIE_1900-1975.xlsx")){
                                        put("Název", "root.title");
                                        put("Autor", "authors");
                                        put("Producent balíčku", "physical_locations.facet");
                                        put("Datum vydání", "date.str");
                                        put("Identifikátor ČNB", "id_ccnb");
                                        put("Identifikátor UUID", "pid");
                                        put("Datum importu", "imported");
                                        put("poslední žijící autor (jméno)", "last_author");
                                        put("rok úmrtí posledního z autorů (RRRR)", "year_of_death_last_author");
                                        put("zdroj", "source");
                                        put("veřejné od", "public_since");
                                        put("Poznámky", "note");
                                        put("Kontrola NK:", "check_NK");
                                        put("Kontrola MZK:", "check_MZK");
                                        if(tsvFile.getName().equals("kontrola_MONOGRAFIE_1900-1975.xlsx - 1900.tsv")){
                                            put("Kontroloval (XX)", "checked_by");
                                            put("Zveřejnit", "public");
                                        }
                                        else{
                                            put("kontroloval", "checked_by");
                                            put("zveřejnit (ANO/NE)", "public");
                                        }
                                    }
                                    else
                                    System.out.println("Skipping unsupported file: " + tsvFile.getName());
                                }
                            }
                        }
                    };

                    for (CSVRecord record : parser) {
                        SolrInputDocument doc = new SolrInputDocument();
                        int recordNumber = (int) record.getRecordNumber() + 1; // +1 to account for header
                        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
                            String header = headers.get(entry.getKey());
                            String Originalvalue = entry.getValue();
                            try {
                                String value = entry.getValue();
                                /*if("1926-".equals(value)){
                                    System.out.println("Debug");
                                }*/

                                if (header != null) {
                                    switch (header) {
                                        case "authors" -> {
                                            String[] authors = value.split("  ");
                                            doc.addField(header, authors);
                                            /*if(authors.length > 1) {
                                            System.out.println("Multiple authors found: "+ authors.length);
                                            for (String author : authors) {
                                                System.out.println(author);
                                            }
                                        }*/
                                        }
                                        case "imported" ->{
                                            String val = entry.getValue();
                                            if(val != null && !val.isEmpty()){
                                            String[] imported = val.split("\\.");
                                            doc.addField(header,String.format("%04d-%02d-%02dT00:00:00Z", Integer.valueOf(imported[2]), Integer.valueOf(imported[1]), Integer.valueOf(imported[0])));
                                            }else{
                                                System.err.println("Empty imported date on row: "+ recordNumber);
                                            }
                                    }
                                        case "date.str" -> {
                                            doc.addField(header, value);
                                            if (value == null || value.isEmpty()) {
                                                System.err.println("Empty date on row: "+ recordNumber);
                                                continue;
                                            }
                                            


                                            if (value.contains("asi")) {
                                                String year = value.replaceAll("[^0-9]", "");
                                                if (year.length() == 4) {
                                                    value = year;
                                                } else {
                                                    System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                }
                                            }else if (value.contains("okolo r. 1900")){
                                                value = "1900?";
                                            }
                                            else if (value.contains("chybně")){
                                                value = value.split(",")[0].trim();
                                            }
                                             else if (value.contains("mezi")) {
                                                value = value.replace("mezi r.", "mezi");
                                                String[] parts = value.split(" ");
                                                switch (parts.length) {
                                                    case 2 ->
                                                        value = parts[1];
                                                    case 4 ->
                                                        value = parts[1] + "-" + parts[3];
                                                    default ->
                                                        System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                }
                                            }
                                            else if (value.contains("po")){
                                                
                                                value = value.replace("po r.", "po ");
                                                value = value.replaceAll("[\\[\\]]", "");
                                                String[] parts = Arrays.stream(value.split(" "))
                                                    .filter(s -> !s.isEmpty())
                                                    .toArray(String[]::new);
                                                if(parts.length != 2){
                                                    System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                    
                                                }else{
                                                    String year = parts[1];
                                                    if(year.length() == 4){
                                                        add_year_since(year, doc, true);
                                                    }else
                                                        add_year_since(parts[1].substring(0, 4), doc,false );
                                                }
                                                continue;
                                            }
                                            else if (value.contains("před")){
                                                value = value.replace("před r.", "před");
                                                value = value.replaceAll("[\\[\\]]", "");
                                                String[] parts = value.split(" ");
                                                if(parts.length != 2){
                                                    System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                    
                                                }else{
                                                    String year = parts[1];
                                                    if(year.length() == 4){
                                                        add_year_before(year, doc, true);
                                                    }else
                                                        add_year_before(parts[1].substring(0, 4), doc,false );
                                                }
                                                continue;
                                            }
                                            else if (value.contains("nebo")){
                                                if(!value.contains(" nebo")){value = value.replace("nebo", " nebo");}
                                                if(!value.contains("nebo ")){value = value.replace("nebo", "nebo ");}
                                                String[] parts = value.split(" ");
                                                if(parts.length !=3){
                                                    System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));}
                                                else{
                                                    value = parts[0] +"?-"+ parts[2]+"?";
                                                }
                                            }
                                            if(value.contains(",")){
                                                String og = value;
                                                value = value.split(",")[0].trim();
                                                if(show_comma_info)System.out.println("Comma found in "+og+", using first part: " + value+" Row: "+ recordNumber);
                                            }
                                            if(value.contains("19--") && value.length() <=6) {// Handle 19-- format with or without []
                                                value = "1900-1999";
                                            }
                                            else if(value.contains("19-") && value.length() <=5) {// Handle 19- format with or without []
                                                value = "1900-1999";
                                            }
                                            value = value.replaceAll("19(\\d)-", "19$10-19$19");
                                            if(value.length()==5) {//Fixing typo of missing brackets
                                                if(value.endsWith("]"))
                                                    value = '['+value;
                                                else if(value.startsWith("[")){
                                                    value = value+']';
                                                }
                                            }
                                            value = value.replaceAll("\\[?19--\\??\\]?", "19");
                                            value = value.replaceAll("\\s+až\\s+", "-");//replace až with dash
                                            value = value.replaceAll("kolem r.\\s+", "?");//replace až with dash
                                            value = value.replaceAll("\\s+a\\s+", "-");//replace a with dash
                                            value = value.replaceAll("ca\\.?\\s+", "?");//replace ca with ?
                                            value = value.replaceAll("[\\[\\]]", "");//Just purge the brackets.
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
                                                    if(yearStart.contains("[")||yearStart.contains("?"))confirmedStart = false;
                                                    if(yearEnd.contains("]")||yearEnd.contains("?"))confirmedEnd = false;
                                                    if(yearEnd.isEmpty()) {
                                                        yearEnd = "1999";
                                                        confirmedEnd = false;
                                                    }

                                                    yearStart = yearStart.replaceAll("[\\[\\]\\?]", "");
                                                    yearEnd = yearEnd.replaceAll("[\\[\\]\\?]", "");

                                                    if("?".equals(yearStart)) {
                                                            yearStart = "1900";
                                                        }
                                                        if("?".equals(yearEnd)) {
                                                            yearEnd = "1999";
                                                        }
                                                    if (yearStart.length() < 4) {
                                                        confirmedStart = false;
                                                        while (yearStart.length() < 4) {
                                                            yearStart+="0";
                                                        }
                                                        //System.err.println("Start year or End year are too short " + tsvFile.getName() + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                    }
                                                    if(yearEnd.length() < 4){
                                                        if("19".equals(yearEnd)){
                                                            confirmedEnd = false;
                                                            yearEnd = "1999";
                                                        }
                                                        else{
                                                            confirmedEnd = true;//This is obscuring ?
                                                            yearEnd = "19"+yearEnd;
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
                                                            System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                        }
                                                    } else if (year.length() == 6 || value.length() == 7) {
                                                        year = year.substring(1, 5);
                                                    }
                                                    else if (year.length() == 2) {
                                                        year =  year+"00";
                                                    }
                                                    if (NumberUtils.isNumber(year)) {
                                                        if(value.contains(year+"-")){
                                                            add_year_since(year, doc, confirmed);
                                                        }else addyear(year, doc, confirmed); 
                                                    }else {
                                                        System.err.println("Failed to parse date on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                    }
                                                }
                                                default ->
                                                    System.err.println("Unexpected date format on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                            }

                                        }
                                        case "pid" -> {
                                            if (!entry.getValue().startsWith("uuid:")) {
                                                doc.addField(header, "uuid:" + entry.getValue());
                                            } else {
                                                doc.addField(header, entry.getValue());
                                            }
                                        }
                                        case "public"->{
                                            String val = entry.getValue().toLowerCase();
                                            doc.addField("public_string", (String)entry.getValue());
                                            if(null == val ){
                                                System.err.println("Unexpected boolean value on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                            }else switch (val) {
                                            case "ano", "yes", "true" -> doc.addField(header, true);
                                            case "ne", "no", "false" -> doc.addField(header, false);
                                            default -> {}
                                        }
                                        }
                                        case "year_of_death_last_author","public_since"->{
                                            String val = entry.getValue();
                                            if(val != null && !val.isEmpty()){
                                                if(val.length() == 4 && NumberUtils.isNumber(val)){
                                                    doc.addField(header, val);
                                                }else{
                                                    //System.err.println("Unexpected year value on row: "+ recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
                                                }
                                            }
                                        }
                                        default ->
                                            doc.addField(header, entry.getValue());
                                    }
                                }
                            } catch (IndexOutOfBoundsException e) {
                                System.err.println("Caught exception in" + tsvFile.getName() + ": " + e.getMessage() + "\n\tVALUE: " + Originalvalue);
                            }
                        }
                        solrClient.add(doc);
                    }

                } catch (Exception e) {
                    System.err.println("Critical Exception: " + tsvFile.getName() + ": " + e.getMessage());
                }
            }

            solrClient.commit();
            System.out.println("All TSV files indexed successfully.");

        } catch (Exception e) {
            System.err.println("Error indexing data: " + e.getMessage());
        }
    }
}
