package cz.inovatika;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrInputDocument;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlProcessor {
    public static AutorData[] processBiblioMods(Document xml, SolrInputDocument doc) throws IOException, InterruptedException {
        if (xml == null) {
            return null;
        }
        NodeList nodes = xml.getElementsByTagNameNS("http://www.loc.gov/mods/v3","identifier");
        for (int i = 0; i < nodes.getLength(); i++) {
            String type = ((Element) nodes.item(i)).getAttribute("type");
            doc.addField("id_" + type, nodes.item(i).getTextContent());
        }
        return ProcessNameIdentifier(xml);
    }

    private static AutorData[] ProcessNameIdentifier(Document xml) throws IOException, InterruptedException {
        NodeList modsName = xml.getElementsByTagNameNS("http://www.loc.gov/mods/v3", "name");
        //NodeList modsName = xml.getElementsByTagName("mods:name");
        AutorData[] nameIDArr = new AutorData[modsName.getLength()];

        for (int x = 0; x < nameIDArr.length; x++) {
            Element name = (Element) modsName.item(x);

            String full = null;
            String family = null;
            String given = null;
            String date = null;
            String termsOfAddress = null;
            String nameIdentifier = null;
            List<String> role = new ArrayList<>();

            NodeList nameParts = getNodelistByLocalTagName(name,"namePart");
            if (nameParts != null && nameParts.getLength() > 0) {
                for (int i = 0; i < nameParts.getLength(); i++) {
                    Node item = nameParts.item(i);
                    String type = ((Element) item).getAttribute("type");
                    if (type == null || type.isEmpty()) {
                        full = item.getTextContent();
                        String[] splitName = item.getTextContent().split(",");
                        if (splitName.length == 2) {
                            family = splitName[0].trim();
                            given = splitName[1].trim();
                        }
                    } else if (type.equals("family") || type.equals("given")) {
                        String fam = "";
                        String giv = "";
                        switch (type) {
                            case "family" -> {
                                fam = item.getTextContent();
                                if (i + 1 < nameParts.getLength()) {
                                    Node next = nameParts.item(i + 1);
                                    String nexttype = ((Element) next).getAttribute("type");
                                    if (nexttype.equals("given")) {
                                        giv = next.getTextContent();
                                    }
                                }
                            }
                            case "given" -> {
                                giv = item.getTextContent();
                                if (i + 1 < nameParts.getLength()) {
                                    Node next = nameParts.item(i + 1);
                                    String nexttype = ((Element) next).getAttribute("type");
                                    if (nexttype.equals("family")) {
                                        fam = next.getTextContent();
                                    }
                                }
                            }

                        }
                        family = fam;
                        given = giv;
                    } else if (type.equals("date")) {
                        date = item.getTextContent();
                    } else if (type.equals("termsOfAddress")) {
                        termsOfAddress = item.getTextContent();
                    } else {
                        System.out.println("Disregarded namePart type: " + type + " :" + item.getTextContent());
                    }
                }

            }

            Element nameIDx = getElementByLocalTagName(name,"nameIdentifier");
            if (nameIDx != null) {
                nameIdentifier = nameIDx.getTextContent();
            }

            Element rolEl = getElementByLocalTagName(name,"role");
            if (rolEl != null) {
                NodeList roles = getNodelistByLocalTagName(name,"roleTerm");
                for (int i = 0; i < roles.getLength(); i++) {
                    role.add(roles.item(i).getTextContent());
                }
            }

            nameIDArr[x] = new AutorData(full, family, given, date, termsOfAddress, nameIdentifier, role);
        }
        return nameIDArr;
    }
    static Element getElementByLocalTagName(Element name,String localName){
        return (Element)name.getElementsByTagNameNS("http://www.loc.gov/mods/v3",localName).item(0);
    }
    static NodeList getNodelistByLocalTagName(Element name,String localName){
        return name.getElementsByTagNameNS("http://www.loc.gov/mods/v3",localName);
    }
    public static void extractAuthorData(final boolean show_comma_info, final SolrClient solrClient, SolrInputDocument doc,
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

            if(autor.date!=null)parseDateBasic(aDoc, autor.date, recordNumber, autor.date);

            solrClient.add("authors", aDoc);
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
    public static void parseDateBasic(SolrInputDocument doc, String value, int recordNumber, String Originalvalue) {
        value = value.replaceAll("asi\\s*", "?").trim();
        String[] parts = value.split("-");
        switch (parts.length) {
            /*case 3 -> {
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
            }*/
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
                /*if (yearEnd.isEmpty()) {
                    yearEnd = "1999";
                    confirmedEnd = false;
                }*/

                yearStart = yearStart.replaceAll("[\\[\\]\\?]", "");
                yearEnd = yearEnd.replaceAll("[\\[\\]\\?]", "");
                /*
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
                */
                if (yearStart.isEmpty()) {
                    yearStart = "0";
                    confirmedStart = false;
                }
                if (yearEnd.isEmpty()) {
                    yearEnd = "9999";
                    confirmedEnd = false;
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
    }
    private static void dateFormatError(int recordNumber, String Originalvalue, String value) {
        System.err.println("Unexpected date format on row: " + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
    }
    private static void dateFormatError(int recordNumber, String Originalvalue, String value, String message) {
        System.err.println(message + " on row: " + recordNumber + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
    }
    static void addyear(String year, SolrInputDocument doc, boolean confirmed) {
        add_year_range(year, confirmed, year, confirmed, doc);
    }
    static void add_year_since(String year, SolrInputDocument doc, boolean confirmed) {
        add_year_range(year, confirmed, "1999", false, doc);
    }
    public static void add_year_range(String year_start, boolean confirmed_start, String year_end, boolean confirmed_end, SolrInputDocument doc) {

        try {
            int yearStart = Integer.parseInt(year_start);
            int yearEnd = Integer.parseInt(year_end);
            if (yearStart > yearEnd || yearEnd > 2025) {//TODO: Get current year (or the next one even better)
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

}