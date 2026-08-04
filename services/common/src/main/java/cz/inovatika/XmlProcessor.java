package cz.inovatika;

//import cz.inovatika.Constants.AuthorsCollectionFields.AppDataFields;

import java.text.Normalizer;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Logger;

import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.commons.lang3.math.NumberUtils;

public class XmlProcessor {

    public static final Logger LOGGER = Logger.getLogger(XmlProcessor.class.getName());
    private static final Pattern IMPRECISE_ISSUED_DATE_HINT = Pattern.compile("^\\[?\\s*(?:(\\d{2})--?|(\\d{3})-)\\??\\s*\\]?$");


//    public static AuthorData[] processBiblioMods(Document xml, SolrInputDocument doc) throws IOException, InterruptedException {
//        if (xml == null) {
//            return null;
//        }
//        NodeList nodes = xml.getElementsByTagNameNS("http://www.loc.gov/mods/v3", "identifier");
//        for (int i = 0; i < nodes.getLength(); i++) {
//            String type = ((Element) nodes.item(i)).getAttribute("type");
//            doc.addField("id_" + type, nodes.item(i).getTextContent());
//        }
//        return ProcessNameIdentifier(xml);
//    }
//
//    private static AuthorData[] ProcessNameIdentifier(Document xml) throws IOException, InterruptedException {
//        NodeList modsName = xml.getElementsByTagNameNS("http://www.loc.gov/mods/v3", "name");
//        AuthorData[] nameIDArr = new AuthorData[modsName.getLength()];
//
//        for (int x = 0; x < nameIDArr.length; x++) {
//            Element name = (Element) modsName.item(x);
//
//            String full = null;
//            String family = null;
//            String given = null;
//            String date = null;
//            String termsOfAddress = null;
//            String nameIdentifier = null;
//            List<String> role = new ArrayList<>();
//
//            NodeList nameParts = getNodelistByLocalTagName(name, "namePart");
//            if (nameParts != null && nameParts.getLength() > 0) {
//                for (int i = 0; i < nameParts.getLength(); i++) {
//                    Node item = nameParts.item(i);
//                    String type = ((Element) item).getAttribute("type");
//                    if (type == null || type.isEmpty()) {
//                        full = item.getTextContent();
//                        String[] splitName = item.getTextContent().split(",");
//                        if (splitName.length == 2) {
//                            family = splitName[0].trim();
//                            given = splitName[1].trim();
//                        }
//                    } else if (type.equals("family") || type.equals("given")) {
//                        String fam = "";
//                        String giv = "";
//                        switch (type) {
//                            case "family" -> {
//                                fam = item.getTextContent();
//                                if (i + 1 < nameParts.getLength()) {
//                                    Node next = nameParts.item(i + 1);
//                                    String nexttype = ((Element) next).getAttribute("type");
//                                    if (nexttype.equals("given")) {
//                                        giv = next.getTextContent();
//                                    }
//                                }
//                            }
//                            case "given" -> {
//                                giv = item.getTextContent();
//                                if (i + 1 < nameParts.getLength()) {
//                                    Node next = nameParts.item(i + 1);
//                                    String nexttype = ((Element) next).getAttribute("type");
//                                    if (nexttype.equals("family")) {
//                                        fam = next.getTextContent();
//                                    }
//                                }
//                            }
//
//                        }
//                        family = fam;
//                        given = giv;
//                    } else if (type.equals("date")) {
//                        date = item.getTextContent();
//                    } else if (type.equals("termsOfAddress")) {
//                        termsOfAddress = item.getTextContent();
//                    } else {
//                        System.out.println("Disregarded namePart type: " + type + " :" + item.getTextContent());
//                    }
//                }
//
//            }
//
//            Element nameIDx = getElementByLocalTagName(name, "nameIdentifier");
//            if (nameIDx != null) {
//                nameIdentifier = nameIDx.getTextContent();
//            }
//
//            Element rolEl = getElementByLocalTagName(name, "role");
//            if (rolEl != null) {
//                NodeList roles = getNodelistByLocalTagName(name, "roleTerm");
//                for (int i = 0; i < roles.getLength(); i++) {
//                    role.add(roles.item(i).getTextContent());
//                }
//            }
//
//            nameIDArr[x] = new AuthorData(full, family, given, date, termsOfAddress, nameIdentifier, role);
//        }
//        return nameIDArr;
//    }
/*
    static Element getElementByLocalTagName(Element name, String localName) {
        return (Element) name.getElementsByTagNameNS("http://www.loc.gov/mods/v3", localName).item(0);
    }

    static NodeList getNodelistByLocalTagName(Element name, String localName) {
        return name.getElementsByTagNameNS("http://www.loc.gov/mods/v3", localName);
    }
*/
    /*
    public static List<SolrInputDocumentWrapper> extractAuthorData(final List<AuthorData> authors) {
        if (authors == null) {
            return null;
        }
        return authors.stream().map(author-> {
            SolrInputDocumentWrapper aDoc = new SolrInputDocumentWrapper(
                    Constants.TitlesCollectionFields.KramFields.PID_FIELD,
                    SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);
            aDoc.addField(AppDataFields.NAME_IDENTIFIER_FIELD, author.getNameIdentifier());
            if (author.getNameIdentifier() != null) {
                aDoc.addField(AppDataFields.ID_FIELD, author.getNameIdentifier());
            } else {
                aDoc.addField(AppDataFields.ID_FIELD, normalize(author.getFull() + author.getDate()));
            }
            aDoc.addField(AppDataFields.FULL_FIELD, author.getFull());
            aDoc.addField(AppDataFields.FAMILY_FIELD, author.getFamily());
            aDoc.addField(AppDataFields.GIVEN_FIELD, author.getGiven());
            aDoc.addField(Constants.AuthorsCollectionFields.AppComputedFields.AUTHOR_DATE_STR, author.getDate());
            aDoc.addField(AppDataFields.TERMS_OF_ADDRESS_FIELD, author.getTermsOfAddress());

            if (author.getDate() != null) {
                parseDateBasic(aDoc, author.getDate(), 0, author.getDate());
            }
            return aDoc;
        }).collect(Collectors.toList());
    }
    */

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

    public static void parseDateBasic(SolrInputDocumentWrapper doc, String value, String where, String Originalvalue,List<String> labels,List<String> notes) {
        value = normalizeImpreciseIssuedDateHint(value, labels);
        value = value.replaceAll("ca\\.?\\s*", "?");//replace ca with ?
        value = value.replaceAll("asi\\s*", "?").trim();
        String[] parts = value.split("-");
        switch (parts.length) {
            /*case 3 -> {
                String yearStart = parts[0].trim();
                String yearEnd = parts[2].trim();
                if (yearEnd.length() == 2 || yearEnd.length() == 3) {
                    yearEnd = "19" + yearEnd; // Assuming 20th century for 2-digit years
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

                yearStart = yearStart.replaceAll("[\\[\\]?]", "");
                yearEnd = yearEnd.replaceAll("[\\[\\]?]", "");
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
                        confirmedEnd, doc,labels,notes);

            }
            case 1 -> {
                String year = parts[0].trim();
                boolean confirmed = year.length() == 4;
                boolean confirmed_start = true;
                boolean confirmed_end = true;
                if (year.length() == 5) {
                    if (year.contains("?")) {
                        if (year.indexOf('?') == 0) {
                            year = year.substring(1, 5);
                        } else {
                            year = year.substring(0, 4);
                        }

                    } else if (year.startsWith("c")) {
                        year = year.substring(1, 5);
                        confirmed = true;
                    }else if (year.startsWith("[")) {
                        year = year.substring(1, 5);
                        confirmed_start = false;
                    }else if (year.endsWith("]")) {
                        year = year.substring(0, 4);
                        confirmed_end = false;
                    } else {
                        dateFormatError(where, Originalvalue, value,labels,notes);
                    }
                } else if (year.length() == 6 || value.length() == 7) {
                    year = year.substring(1, 5);
                } else if (year.length() == 2) {
                    year = year + "00";
                }
                if (NumberUtils.isCreatable(year)) {
                    if(!confirmed_start || !confirmed_end){
                        add_year_range(year, confirmed_start, year, confirmed_end, doc,labels,notes);
                    }
                    else if (value.contains(year + "-")) {
                        add_year_since(year, doc, confirmed,labels,notes);
                    } else {
                        addyear(year, doc, confirmed,labels,notes);
                    }
                } else {
                    dateFormatError(where, Originalvalue, value, Constants.TitlesCollectionFields.Labels.EXCEL_FAILED_TO_PARSE_DATE,labels,notes);
                }
            }
            default ->
                dateFormatError(where, Originalvalue, value,labels,notes);
        }
    }

    private static void dateFormatError(String where, String Originalvalue, String value,List<String> labels,List<String> notes) {
        dateFormatError(where,Originalvalue,value,Constants.TitlesCollectionFields.Labels.UNEXPECTED_DATE_FORMAT,labels,notes);}

    private static void dateFormatError(String where, String Originalvalue, String value, String message,List<String> labels,List<String> notes) {
        //LOGGER.severe(message + recNum + "\n\tVALUE: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
        notes.add(message + where + " Hodnota: " + ((Originalvalue.equals(value)) ? Originalvalue : Originalvalue + " _ " + value));
        labels.add( message);
    }

    static void addyear(String year, SolrInputDocumentWrapper doc, boolean confirmed,List<String> labels,List<String> notes) {
        add_year_range(year, confirmed, year, confirmed, doc,labels,notes);
    }

    static void add_year_since(String year, SolrInputDocumentWrapper doc, boolean confirmed,List<String> labels,List<String> notes) {
        add_year_range(year, confirmed, "1999", false, doc,labels,notes);
    }

    public static void add_year_range(String year_start, boolean confirmed_start, String year_end, boolean confirmed_end, SolrInputDocumentWrapper doc,List<String> labels,List<String> notes) {

        try {
            int yearStart = Integer.parseInt(year_start);
            int yearEnd = Integer.parseInt(year_end);
            if (yearStart > yearEnd || yearEnd > Year.now().getValue()) {
                labels.add( Constants.TitlesCollectionFields.Labels.EXCEL_SUSPICIOUS_YEAR_RANGE);
            }
            doc.setField(Constants.TitlesCollectionFields.AppComputedFields.DATE_ISSUED_YEAR, yearEnd);
            //doc.setField(Constants.TitlesCollectionFields.AppDataFields.DATE_ISSUED_END_YEAR_FIELD, yearEnd);
            //doc.setField(Constants.TitlesCollectionFields.AppDataFields.DATE_ISSUED_START_YEAR_FIELD, yearStart);

            /*
            doc.addField("date_range_start.year", yearStart);
            doc.addField("date_range_end.year", yearEnd);
            doc.addField("date.min", String.format("%04d-01-01T00:00:00Z", yearStart));
            doc.addField("date.max", String.format("%04d-12-31T00:00:00Z", yearEnd));
            //doc.addField("date_range_start.month", "1");
            //doc.addField("date_range_end.month", "12");
            */

            //doc.addField(AppComputedFields.CONFIRMED_START_YEAR, confirmed_start);
            //doc.addField(AppComputedFields.CONFIRMED_END_YEAR, confirmed_end);
        } catch (NumberFormatException e) {
            notes.add( "Failed to parse year range: " + year_start + "-" + year_end);
            labels.add( Constants.TitlesCollectionFields.Labels.EXCEL_FAILED_TO_PARSE_DATE);
        }
    }

    public static String normalizeImpreciseIssuedDateHint(String value, List<String> labels) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim()
                .replace('–', '-')
                .replace('—', '-');
        Matcher matcher = IMPRECISE_ISSUED_DATE_HINT.matcher(normalized);
        if (!matcher.matches()) {
            return value;
        }

        String prefix = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        int missingDigits = 4 - prefix.length();
        if (labels != null && !labels.contains(Constants.TitlesCollectionFields.Labels.IMPRECISE_DATE_ISSUED)) {
            labels.add(Constants.TitlesCollectionFields.Labels.IMPRECISE_DATE_ISSUED);
        }
        return prefix + "0".repeat(missingDigits) + "?-" + prefix + "9".repeat(missingDigits) + "?";
    }
/*
    public static void trySetLastAuthor(SolrInputDocumentWrapper doc, List<AuthorData> nameIDArray) {
        Object field = doc.getFieldValue("last_author");
        String last_author = field != null ? field.toString() : null;

        try {
            if (last_author != null && (nameIDArray != null)) {
                for (AuthorData autor : nameIDArray) {
                    if (similarityMatch(normalize(last_author), normalize(autor.getFull()), .5)) {
                        autor.setLast( true);
                        doc.setField("last_author_nameIdentifier", autor.getNameIdentifier());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(),e);
        }
    }*/

    public static String replaceWordsWithQMarks(String value, String where, String Originalvalue,List<String> labels,List<String> notes) {
        if (value.contains("asi")) {
            String year = value.replaceAll("[^0-9]", "");
            if (year.length() == 4) {
                value = year + "?";
            } else {
                dateFormatError(where, Originalvalue, value,labels,notes);
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
                    dateFormatError(where, Originalvalue, value,labels,notes);
            }
        } else if (value.contains("po")) {
            value = value.replace("po roce", "po");
            value = value.replace("po r.", "po ");
            value = value.replaceAll("[\\[\\]]", "");
            String[] parts = Arrays.stream(value.split(" "))
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            if (parts.length != 2) {
                dateFormatError(where, Originalvalue, value,labels,notes);

            } else {
                String year = parts[1];
                value = year + "-?";
                /*if (year.length() == 4) {
                    add_year_since(year, doc, true);
                } else {
                    add_year_since(parts[1].substring(0, 4), doc, false);
                }*/
            }
        } else if (value.contains("před")) {
            value = value.replace("před r.", "před");
            value = value.replaceAll("[\\[\\]]", "");
            String[] parts = value.split(" ");
            if (parts.length != 2) {
                dateFormatError(where, Originalvalue, value,labels,notes);

            } else {
                String year = parts[1];
                value = "?-"+year;
                /*if (year.length() == 4) {
                    add_year_before(year, doc, true);
                } else {
                    add_year_before(parts[1].substring(0, 4), doc, false);
                }*/
            }
        } else if (value.contains("nebo")) {
            if (!value.contains(" nebo")) {
                value = value.replace("nebo", " nebo");
            }
            if (!value.contains("nebo ")) {
                value = value.replace("nebo", "nebo ");
            }
            String[] parts = value.split(" ");
            if (parts.length != 3) {
                dateFormatError(where, Originalvalue, value,labels,notes);
            } else {
                value = parts[0] + "?-" + parts[2] + "?";
            }
        }
        return value;
    }

}
