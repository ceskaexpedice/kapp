package cz.inovatika.authors;

import java.util.ArrayList;
import java.util.List;

import cz.inovatika.XmlProcessor;
import cz.inovatika.utils.XMLUtils;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jetty.util.StringUtil;
import org.json.JSONObject;
import org.w3c.dom.Element;

public class AuthorData {

    private String full;
    private String family;
    private String given;
    private String date;
    private String termsOfAddress;
    private String nameIdentifier;
    private List<String> role = new ArrayList<>();
    boolean isLast;


    private AuthorData() {}

    public List<String> getRole() {
        return role;
    }

    public void setRole(List<String> role) {
        this.role = role;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }


    public String getGiven() {
        return given;
    }

    public void setGiven(String given) {
        this.given = given;
    }


    public void setFull(String full) {
        this.full = full;
    }

    public String getFull() {
        if (!StringUtil.isEmpty(full)) {
            return full;
        } else {
            return family +", "+ given;
        }
    }

    public String getNameIdentifier() {
        return nameIdentifier;
    }

    public void setNameIdentifier(String nameIdentifier) {
        this.nameIdentifier = nameIdentifier;
    }

    public String getTermsOfAddress() {
        return termsOfAddress;
    }

    public void setTermsOfAddress(String termsOfAddress) {
        this.termsOfAddress = termsOfAddress;
    }

    public boolean isLast() {
        return isLast;
    }
    public void setLast(boolean last) {
        isLast = last;
    }

    public static AuthorData create(Element nameElm) {
        AuthorData authorData = new AuthorData();

        Element namePartElm = XMLUtils.findElement(nameElm, elm-> {
            String localName = elm.getLocalName();
            boolean typeAttribute =  elm.hasAttribute("type");
            return localName.equals("namePart") && !typeAttribute;
        });
        if (namePartElm != null) {
            authorData.setFull(namePartElm.getTextContent());
        }

        Element familyElm = XMLUtils.findElement(nameElm, elm-> {
            String localName = elm.getLocalName();
            String type = elm.getAttribute("type");
            return localName.equals("namePart") && type.equals("family");
        });
        if (familyElm != null) {
            authorData.setFamily(familyElm.getTextContent());
        }
        Element givenElm = XMLUtils.findElement(nameElm, elm-> {
            String localName = elm.getLocalName();
            String type = elm.getAttribute("type");
            return localName.equals("namePart") && type.equals("given");
        });
        if (givenElm != null) {
            authorData.setGiven(givenElm.getTextContent());
        }
        Element dateElm = XMLUtils.findElement(nameElm, elm-> {
            String localName = elm.getLocalName();
            String type = elm.getAttribute("type");
            return localName.equals("namePart") && type.equals("date");
        });
        if (dateElm != null) {
            authorData.setDate(dateElm.getTextContent());
        }

        Element identifierElm = XMLUtils.findElement(nameElm, elm-> {
            String localName = elm.getLocalName();
            return localName.equals("nameIdentifier");
        });
        if (identifierElm != null) {
            authorData.setNameIdentifier(identifierElm.getTextContent());
        }

        List<String> roleTerms = XMLUtils.getElementsRecursive(nameElm, elm-> {
            String localName = elm.getLocalName();
            return localName.equals("roleTerm");
        }).stream().map(Element::getTextContent).toList();
        if (!roleTerms.isEmpty()) {
            authorData.setRole(roleTerms);
        }
        return authorData;
    }

    public SolrInputDocument toAuthoritySolrDocument() {
        SolrInputDocument aDoc = new SolrInputDocument();
        aDoc.addField("nameIdentifier", this.getNameIdentifier());
        if (this.getNameIdentifier() != null) {
            aDoc.addField("id", this.getNameIdentifier());
        } else {
            aDoc.addField("id", XmlProcessor.normalize(this.getFull() + this.getDate()));
        }
        aDoc.addField("full", this.getFull());
        aDoc.addField("family", this.getFamily());
        aDoc.addField("given", this.getGiven());
        aDoc.addField("date", this.getDate());
        aDoc.addField("termsOfAddress", this.getTermsOfAddress());

        if (this.getDate() != null) {
            XmlProcessor.parseDateBasic(aDoc, this.getDate(), -1, this.getDate());
        }
        return aDoc;

    }

    public String toJsonString() {
        JSONObject json = new JSONObject();
        json.put("full", getFull());
        json.put("family", family);
        json.put("given", given);
        json.put("date", date);
        json.put("role", role);
        json.put("termsOfAddress", termsOfAddress);

        if (this.getNameIdentifier() != null) {
            json.put("nameIdentifier", this.getNameIdentifier());
        } else {
            json.put("nameIdentifier", XmlProcessor.normalize(this.getFull() + this.getDate()));
        }

        json.put("isLast", isLast);
        return json.toString();
    }

    @Override
    public String toString() {
        return "AuthorData{" +
                "full='" + getFull() + '\'' +
                ", family='" + family + '\'' +
                ", given='" + given + '\'' +
                ", date='" + date + '\'' +
                ", termsOfAddress='" + termsOfAddress + '\'' +
                ", nameIdentifier='" + nameIdentifier + '\'' +
                ", role=" + role +
                ", isLast=" + isLast +
                '}';
    }
}