package cz.inovatika.authors;

import cz.inovatika.utils.XMLUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class AuthorDataUtils {

    private AuthorDataUtils() {}

    public static List<AuthorData> parseAuthorData(Document biblioMods) {
        Element modsElm = XMLUtils.findElement(biblioMods.getDocumentElement(), elm-> elm.getLocalName().equals("mods"));
        if (modsElm != null) {
            return  XMLUtils.getElements(modsElm, elm -> {
                String localName = elm.getLocalName();
                String type = elm.getAttribute("type");
                return localName.equals("name") && type.equals("personal");
            }).stream().map(AuthorData::create).toList();

        } else return new ArrayList<>();
    }
}
