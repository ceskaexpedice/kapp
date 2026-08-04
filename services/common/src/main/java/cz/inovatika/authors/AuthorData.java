package cz.inovatika.authors;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import cz.inovatika.Constants.AuthorsCollectionFields.AuthComputedFields;
import cz.inovatika.Constants.AuthorsCollectionFields.AuthDataFields;
import cz.inovatika.XmlProcessor;
import cz.inovatika.dates.DateRange;
import cz.inovatika.dates.DateRangeParser;
import cz.inovatika.dates.normalization.utils.AuthorNormalizationUtils;
import cz.inovatika.utils.XMLUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.SolrInputDocument;
import org.eclipse.jetty.util.StringUtil;
import org.json.JSONObject;
import org.w3c.dom.Element;

public class AuthorData {

    public static final Logger LOGGER = Logger.getLogger(AuthorData.class.getName());

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
        aDoc.addField(AuthDataFields.NAME_IDENTIFIER_FIELD, this.getNameIdentifier());
        if (this.getNameIdentifier() != null) {
            aDoc.addField(AuthDataFields.ID_FIELD, this.getNameIdentifier());
        } else {
            String name = this.getFull() + (this.getDate() != null ? this.getDate() : "") ;
            String normalizedName = XmlProcessor.normalize(name);
            if (StringUtils.isNotBlank(normalizedName)) {
                aDoc.addField(AuthDataFields.ID_FIELD, XmlProcessor.normalize(name));
            } else {
                LOGGER.warning(String.format("Normalized name is %s", normalizedName));
                aDoc.addField(AuthDataFields.ID_FIELD, name);
            }
        }
        aDoc.addField(AuthDataFields.FULL_FIELD, this.getFull());
        aDoc.addField(AuthDataFields.FAMILY_FIELD, this.getFamily());
        aDoc.addField(AuthDataFields.GIVEN_FIELD, this.getGiven());
        aDoc.addField(AuthDataFields.TERMS_OF_ADDRESS_FIELD, this.getTermsOfAddress());

        if (this.getDate() != null) {
            DateRange parsed = (new DateRangeParser()).parse(AuthorNormalizationUtils.normalize(date));
            if (parsed != null) {
                aDoc.setField(AuthComputedFields.AUTHOR_DATE_STR, date);
                if (parsed.startYear() != null) {
                    if (parsed.startYear().equals("0") && !parsed.startConfirmed()) {
                        aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
                    } else {
                        aDoc.addField(AuthComputedFields.AUTHOR_YEAR_BIRTH, parsed.startYear());
                        aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, parsed.startConfirmed());
                    }
                }
                if (parsed.endYear() != null) {
                    if (parsed.endYear().equals("9999") && !parsed.endConfirmed()) {
                        aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
                    } else {
                        aDoc.addField(AuthComputedFields.AUTHOR_YEAR_DEATH, parsed.endYear());
                        aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, parsed.endConfirmed());
                    }
                }
            } else {
                aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
                aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
                LOGGER.warning(String.format( "Could not parse date from  %s" , date));
            }
        } else {
            aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
            aDoc.addField(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
        }
        return aDoc;
    }

    public String toJsonString() {
        JSONObject json = new JSONObject();
        json.put(AuthDataFields.FULL_FIELD, getFull());
        json.put(AuthDataFields.FAMILY_FIELD, family);
        json.put(AuthDataFields.GIVEN_FIELD, given);
        json.put(AuthComputedFields.AUTHOR_DATE_STR, date);
        json.put(AuthDataFields.ROLE_FIELD, role);
        json.put(AuthDataFields.TERMS_OF_ADDRESS_FIELD, termsOfAddress);

        if (this.getNameIdentifier() != null) {
            json.put(AuthDataFields.ID_FIELD, this.getNameIdentifier());
            json.put(AuthDataFields.NAME_IDENTIFIER_FIELD, this.getNameIdentifier());
        } else {
            String name = this.getFull() + (this.getDate() != null ? this.getDate() : "") ;
            json.put(AuthDataFields.ID_FIELD, XmlProcessor.normalize(name));
        }
        if (this.getDate() != null) {
            DateRange parsed = (new DateRangeParser()).parse(AuthorNormalizationUtils.normalize(date));
            if (parsed != null) {
                json.put(AuthComputedFields.AUTHOR_DATE_STR, date);
                if (parsed.startYear() != null) {
                    if (parsed.startYear().equals("0") && !parsed.startConfirmed()) {
                        json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
                    } else {
                        json.put(AuthComputedFields.AUTHOR_YEAR_BIRTH, parsed.startYear());
                        json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, parsed.startConfirmed());
                    }
                }
                if (parsed.endYear() != null) {
                    if (parsed.endYear().equals("9999") && !parsed.endConfirmed()) {
                        json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
                    } else {
                        json.put(AuthComputedFields.AUTHOR_YEAR_DEATH, parsed.endYear());
                        json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, parsed.endConfirmed());
                    }
                }
            } else {
                json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
                json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
                LOGGER.warning(String.format( "Could not parse date from  %s" , date));
            }
        } else {
            json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_BIRTH, false);
            json.put(AuthComputedFields.AUTHOR_CONFIRMED_YEAR_DEATH, false);
        }


        json.put(AuthDataFields.IS_LAST_FIELD, isLast);
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