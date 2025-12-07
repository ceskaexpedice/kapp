package cz.inovatika.identifiers;

import cz.inovatika.utils.XMLUtils;
import org.w3c.dom.Document;

import javax.lang.model.element.Element;
import java.util.HashMap;
import java.util.Map;

public class IdentifierUtils {

    private IdentifierUtils() {}

    public static Map<String,String> findIdentifiers(Document biblioMods) {
        Map<String,String> map = new HashMap<>();
        XMLUtils.getElementsRecursive(biblioMods.getDocumentElement(), (elm)-> {
            return elm.getLocalName().equals("identifier");
        }).forEach(identEl-> {
            String type = identEl.getAttribute("type");
            String content= identEl.getTextContent();
            map.put(type,content);
        });
        return map;
    }
}
