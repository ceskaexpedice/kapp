package cz.inovatika.utils;

import org.w3c.dom.Element;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class CursorUtils {

    public static final Logger LOGGER = Logger.getLogger(CursorUtils.class.getName());

    private CursorUtils() {}

    public static String findCursorMark(Element elm) {
        Element element = XMLUtils.findElement(elm, new XMLUtils.ElementsFilter() {
            @Override
            public boolean acceptElement(Element element) {
                String nodeName = element.getNodeName();
                boolean nextCursorMark = element.hasAttribute("name") && element.getAttribute("name").equals("nextCursorMark");
                return nodeName.equals("str") && nextCursorMark;
            }
        });
        return element != null ? element.getTextContent() : null;
    }

    public static String findQueryCursorMark(Element elm) {
        Element queryParams = XMLUtils.findElement(elm, new XMLUtils.ElementsFilter() {
            @Override
            public boolean acceptElement(Element element) {
                String nodeName = element.getNodeName();
                String paramName = element.getAttribute("name");
                return nodeName.equals("lst") && paramName.equals("params");

            }
        });
        if (queryParams != null) {
            Element element = XMLUtils.findElement(elm, new XMLUtils.ElementsFilter() {
                @Override
                public boolean acceptElement(Element element) {
                    String nodeName = element.getNodeName();
                    boolean nextCursorMark = element.hasAttribute("name") && element.getAttribute("name").equals("cursorMark");
                    return nodeName.equals("str") && nextCursorMark;
                }
            });
            return element != null ? element.getTextContent() : null;
        }
        return null;
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
