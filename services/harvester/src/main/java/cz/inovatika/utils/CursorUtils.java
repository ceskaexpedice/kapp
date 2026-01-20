package cz.inovatika.utils;

import org.w3c.dom.Element;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

public class CursorUtils {

    public static final Logger LOGGER = Logger.getLogger(CursorUtils.class.getName());

    public static String findCursorMark(Element elm) {
        Element element = XMLUtils.findElement(elm, (Element e) -> {
            boolean nextCursorMark = e.hasAttribute("name") && e.getAttribute("name").equals("nextCursorMark");
            return e.getNodeName().equals("str") && nextCursorMark;
        });
        return element != null ? element.getTextContent() : null;
    }

    public static String findQueryCursorMark(Element elm) {
        Element queryParams = XMLUtils.findElement(elm, (Element element) -> {
            String paramName = element.getAttribute("name");
            return element.getNodeName().equals("lst") && paramName.equals("params");
        });
        if (queryParams == null) return null;
        Element element = XMLUtils.findElement(elm, (Element e) -> {
            boolean nextCursorMark = e.hasAttribute("name") && e.getAttribute("name").equals("cursorMark");
            return e.getNodeName().equals("str") && nextCursorMark;
        });
        return element != null ? element.getTextContent() : null;
        
    }

    public static String pidCursorQuery(String mq, String cursor, int rows, String fq, String endpoint, String identifierField, String sorting, String...flFields)  {
        String flParam = flFields != null && flFields.length > 0 ? identifierField+","+ String.join(",", flFields) :  identifierField;
        String fullQuery = endpoint +"?q="+ mq;
        String coreQuery = cursor != null ? String.format("&rows=%d&cursorMark=%s", rows, cursor) : String.format("&rows=%d&cursorMark=*", rows);
        fullQuery += coreQuery +"&sort=" + URLEncoder.encode(sorting, StandardCharsets.UTF_8)+"&fl="+ flParam;
        if (fq !=null && (!fq.trim().isEmpty())) {
            fullQuery += "&fq=" + URLEncoder.encode(fq, StandardCharsets.UTF_8);
        }

        return fullQuery+"&wt=xml";
    }


}
