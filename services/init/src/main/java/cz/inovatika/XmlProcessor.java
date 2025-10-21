package cz.inovatika;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
}