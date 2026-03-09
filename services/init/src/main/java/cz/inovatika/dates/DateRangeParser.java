package cz.inovatika.dates;

import org.apache.commons.lang3.math.NumberUtils;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DateRangeParser {

    public static final Logger LOGGER = Logger.getLogger(DateRangeParser.class.getName());

    public DateRange parse(String standardValue) {
        String[] parts = standardValue.split("-", 2);
        return switch (parts.length) {
            case 1 -> parseSingleYear(parts[0]);
            case 2 -> parseYearRange(parts[0], parts[1]);
            default -> {
                LOGGER.warning("Invalid date range: " + standardValue);
                yield  null;
            }
        };
    }
    
    private DateRange parseYearRange(String partStart, String partEnd) {
        
        String yearStart = partStart.isEmpty() ? "0" : partStart;
        String yearEnd = partEnd.isEmpty() ? "9999" : partEnd;

        boolean isStartConfirmed = !partStart.contains("?") && partStart.length() == 4;
        boolean isEndConfirmed = !partEnd.contains("?") && partEnd.length() == 4;
        
        yearStart = yearStart.replace("?", "");
        yearEnd = yearEnd.replace("?", "");
        
        if (!NumberUtils.isCreatable(yearStart) || !NumberUtils.isCreatable(yearEnd)) {
            LOGGER.log(Level.SEVERE, "Non-numeric year in range: " + partStart + "-" + partEnd);
            return null;
        }
        return new DateRange(yearStart, yearEnd, isStartConfirmed, isEndConfirmed);
    }

    private DateRange parseSingleYear(String part) {
        boolean confirmed = !part.contains("?");
        String year = part.replace("?", "");
        if (year.length() != 4 || !NumberUtils.isCreatable(year)) {
            LOGGER.log(Level.SEVERE, "Invalid single year format: " + part);
            return null;
        }
        return new DateRange(year, year, confirmed, confirmed);
    }
}