package cz.inovatika.dates;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DateNormalizer {

    public static final Logger LOGGER = Logger.getLogger(DateNormalizer.class.getName());

    public static String normalize(String value) {
        if (value == null || value.isEmpty()) {
            LOGGER.log(Level.WARNING, "value is null or empty");
            return value;
        }

        value = cleanLanguageTerms(value);
        value = cleanPatterns(value);
        value = value.replaceAll("[\\[\\]]", "").trim();
        return value;
    }

    private static String cleanLanguageTerms(String value) {
        if (value.contains("okolo r. 1900")) {
            return "1900?";
        }
        
        if (value.matches("(?i)po\\s+(roce|r\\.)?\\s*(\\d{4}).*")) {
            String year = value.replaceAll("[^0-9]", "");
            return year + "-?";
        }
        return value;
    }
    private static String cleanPatterns(String value) {
        if (value.contains(",")) {
            value = value.split(",")[0].trim();
        }
        
        if (value.matches("19--") || value.matches("\\[?19--\\??\\]?")) {
             return "1900?-1999?";
        }
        value = value.replaceAll("\\s+(až|a)\\s+", "-");
        
        value = value.replaceAll("(?i)kolem\\s+r\\.?\\s*", "?");
        value = value.replaceAll("(?i)ca\\.?\\s*", "?");
        value = value.replaceAll("(?i)asi\\s*", "?");
        
        return value;
    }
}