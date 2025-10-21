package cz.inovatika.utils;

import org.w3c.dom.Element;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
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
        try {
            String flParam = flFields != null && flFields.length > 0 ? identifierField+","+ Arrays.stream(flFields).collect(Collectors.joining(",")) :  identifierField;
            String fullQuery = null;
            if (fq !=null && (!fq.trim().equals(""))) {
                fullQuery = "?q="+ mq + (cursor != null ? String.format("&rows=%d&cursorMark=%s", rows, cursor) : String.format("&rows=%d&cursorMark=*", rows))+"&sort=" + URLEncoder.encode(sorting, "UTF-8")+"&fl="+ flParam +"&fq=" + URLEncoder.encode(fq,"UTF-8");
            } else {
                fullQuery = "?q="+ mq + (cursor != null ? String.format("&rows=%d&cursorMark=%s", rows, cursor) : String.format("&rows=%d&cursorMark=*", rows))+"&sort=" + URLEncoder.encode(sorting, "UTF-8")+"&fl="+ flParam;
            }

            String query = endpoint + fullQuery+"&wt=xml";
            return query;
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }


}
