package cz.inovatika.dates;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DateRangeParserTest {

    @Test
    public void testDateParse() {
        DateRangeParser dateParser1 = new DateRangeParser();
        DateRange parsed1 = dateParser1.parse("2006-2007");
        Assertions.assertNotNull(parsed1);
        Assertions.assertEquals("2006", parsed1.startYear());
        Assertions.assertEquals("2007", parsed1.endYear());


        DateRangeParser dateParser2 = new DateRangeParser();
        DateRange parsed2 = dateParser2.parse("2006?-2007");
        Assertions.assertNotNull(parsed2);
        Assertions.assertEquals("2006", parsed2.startYear());
        Assertions.assertEquals("2007", parsed2.endYear());
        Assertions.assertFalse(parsed2.startConfirmed());
        Assertions.assertTrue(parsed2.endConfirmed());

        DateRangeParser dateParser3 = new DateRangeParser();
        DateRange parsed3 = dateParser3.parse("2006-");
        Assertions.assertNotNull(parsed3);
        Assertions.assertEquals("2006", parsed3.startYear());
        Assertions.assertEquals("9999", parsed3.endYear());
        Assertions.assertTrue(parsed3.startConfirmed());
        Assertions.assertFalse(parsed3.endConfirmed());

        DateRangeParser dateParser4 = new DateRangeParser();
        DateRange parsed4 = dateParser4.parse("-2006");
        Assertions.assertNotNull(dateParser4);
        Assertions.assertEquals("0", parsed4.startYear());
        Assertions.assertEquals("2006", parsed4.endYear());
        Assertions.assertFalse(parsed4.startConfirmed());
        Assertions.assertTrue(parsed4.endConfirmed());

        DateRangeParser dateParser5 = new DateRangeParser();
        DateRange parsed5 = dateParser5.parse("2006?-2007?");
        Assertions.assertNotNull(parsed5);
        Assertions.assertEquals("2006", parsed1.startYear());
        Assertions.assertEquals("2007", parsed1.endYear());
        Assertions.assertFalse(parsed5.startConfirmed());
        Assertions.assertFalse(parsed5.endConfirmed());

    }

}
