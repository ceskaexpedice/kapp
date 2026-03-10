package cz.inovatika.dates.normalization.utils;

import cz.inovatika.dates.normalization.NormalizationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class TitleDateNormalizationUtilsTest {



    @Test
    public void testBetweenKeyword() {
        String raw1 = "mezi 2006 a 2007?";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.betweenKeywords(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "2006-2007",result1.value());

        String raw2 = "ca mezi 1562 a 1586]";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.betweenKeywords(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1562-1586",result2.value());
    }

    @Test
    public void testCopyright() {
        String raw1= "c2014";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.copyRightYears(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "2014",result1.value());

        String raw2= "c2013-";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.copyRightYears(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertFalse(result2.isDone());
        // should not be final ??
        Assertions.assertEquals( "2013-",result2.value());

        String  raw3= "c2010-2012";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.copyRightYears(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals( "2010-2012",result3.value());

        String  raw4= "c2004, c1995";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = TitleNormalizationUtils.copyRightYears(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals( "2004 1995",result4.value());
    }

    @Test
    public void testRemoveUnecessaryChars() {
        String raw1 = "[2000]";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.removeUnnecessaryCharacters(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertFalse(result1.isDone());
        Assertions.assertEquals( "2000",result1.value());

        String raw2 = "[2000?]-";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.removeUnnecessaryCharacters(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertFalse(result2.isDone());
        Assertions.assertEquals( "2000?-",result2.value());

        String raw3 = "[200-]-^^^^^^";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.removeUnnecessaryCharacters(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertFalse(result3.isDone());
        Assertions.assertEquals( "200--",result3.value());
    }

    @Test
    public void testCorrectionsFirstYear() {
        String raw1 = "1996 [v tiráži nespr.] 1995";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.resolveCorrectedDatesFirstDate(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "1996",result1.value());

        String raw2 = "1995 [v tiráži nespr.] 1994";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.resolveCorrectedDatesFirstDate(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1995",result2.value());

        String raw3 = "1977, [na tit. listu nesprávně] 1975";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.resolveCorrectedDatesFirstDate(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals( "1977",result3.value());

    }

    @Test
    public void testCorrectionsSecondYear() {
        String raw1 = "1995 [i.e. 2006]";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "2006",result1.value());

        String raw2 = "1994 [v tiráži spr.] 1995";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1995",result2.value());

        String raw3 = "1977 [i.e. 1980]";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals( "1980",result3.value());

        String raw4 = "1943, [v tiráži] 1944";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals( "1944",result4.value());

        String raw5 = "1941, [spr. 1945]";
        var inputStep5 = new NormalizationStep(raw5, false);
        var result5 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep5);
        Assertions.assertNotNull(result5);
        Assertions.assertTrue(result5.isDone());
        Assertions.assertEquals( "1945",result5.value());

        String raw6 = "1941, [na obálce a v tiráži] 1946";
        var inputStep6 = new NormalizationStep(raw6, false);
        var result6 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep6);
        Assertions.assertNotNull(result6);
        Assertions.assertTrue(result6.isDone());
        Assertions.assertEquals( "1946",result6.value());

        String raw7 = "1944, [dotisk 1945]";
        var inputStep7 = new NormalizationStep(raw7, false);
        var result7 = TitleNormalizationUtils.resolveCorrectedDatesSecondDate(inputStep7);
        Assertions.assertNotNull(result7);
        Assertions.assertTrue(result7.isDone());
        Assertions.assertEquals( "1945",result7.value());
    }

    @Test
    public void testModifyRanges() {
        String raw1 = "1965/66";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.modifyRanges(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "1965-1966",result1.value());

        String raw2 = "2019/20";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.modifyRanges(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "2019-2020",result2.value());

        String raw3 = "17. 1. 1492/1493";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.modifyRanges(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals( "1492-1493",result3.value());

        //1967-19uu
        String raw4 = "1967-19uu";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = TitleNormalizationUtils.modifyRanges(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals( "1967-1999",result4.value());

        String raw5 = "1961-[19--]";
        var inputStep5 = new NormalizationStep(raw5, false);
        var result5 = TitleNormalizationUtils.modifyRanges(inputStep5);
        Assertions.assertNotNull(result5);
        Assertions.assertTrue(result5.isDone());
        Assertions.assertEquals( "1961-1999",result5.value());

        String raw6 = "1961-[19--]";
        var inputStep6 = new NormalizationStep(raw6, false);
        var result6 = TitleNormalizationUtils.modifyRanges(inputStep6);
        Assertions.assertNotNull(result6);
        Assertions.assertTrue(result6.isDone());
        Assertions.assertEquals( "1961-1999",result6.value());

        String raw7 = "[19--]-2000";
        var inputStep7 = new NormalizationStep(raw7, false);
        var result7 = TitleNormalizationUtils.modifyRanges(inputStep7);
        Assertions.assertNotNull(result7);
        Assertions.assertTrue(result7.isDone());
        Assertions.assertEquals( "1900-2000",result7.value());

        String raw8 = "[19--]-19--";
        var inputStep8 = new NormalizationStep(raw8, false);
        var result8 = TitleNormalizationUtils.modifyRanges(inputStep8);
        Assertions.assertNotNull(result8);
        Assertions.assertTrue(result8.isDone());
        Assertions.assertEquals( "1900-1999",result8.value());

        String raw9 = "2003-[2008?]";
        var inputStep9 = new NormalizationStep(raw9, false);
        var result9 = TitleNormalizationUtils.modifyRanges(inputStep9);
        Assertions.assertNotNull(result9);
        Assertions.assertTrue(result9.isDone());
        Assertions.assertEquals( "2003-2008",result9.value());

        String raw10 = "[19--?]-[2014?]";
        var inputStep10 = new NormalizationStep(raw10, false);
        var result10 = TitleNormalizationUtils.modifyRanges(inputStep10);
        Assertions.assertNotNull(result10);
        Assertions.assertTrue(result10.isDone());
        Assertions.assertEquals( "1900-2014",result10.value());

        String raw11 = "2003-";
        var inputStep11 = new NormalizationStep(raw11, false);
        var result11 = TitleNormalizationUtils.modifyRanges(inputStep11);
        Assertions.assertNotNull(result11);
        Assertions.assertFalse(result11.isDone());
        Assertions.assertEquals( "2003-",result11.value());
    }

    @Test
    public void testModifyOpenRanges() {
        String raw1 = "2003-";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.modifyOpenRanges(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "2003-2003",result1.value());

        String raw2 = "[19--]-";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.modifyOpenRanges(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1900-1999",result2.value());

        String raw3 = "[186-?]-";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.modifyOpenRanges(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        // TODO: is it good ?
        Assertions.assertEquals( "1860-1869",result3.value());

    }

    @Test
    public void testExactDate() {
        String raw1 = "17. 12. 1499";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.extractYearFromFullDate(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "1499",result1.value());

        String raw2 = "28.11.2025";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.extractYearFromFullDate(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "2025",result2.value());
    }

    @Test
    public void testSanitizeFinalOutput() {
        String raw1 = "neco abc d 1499-1490";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "1499-1490",result1.value());

        String raw2 = "neco abc d 1988 2000";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1988",result2.value());
    }

    @Test
    public void testSingleYear() {
        String raw1 = "2010";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals( "2010",result1.value());

        String raw2 = "193-?";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals( "1930",result2.value());

        String raw3 = "2009?";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals( "2009",result3.value());

        String raw4 = "2003 1. dotisk 2. vyd";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals( "2003",result4.value());

        String raw5 = "2003 1. dotisk 2. vyd";
        var inputStep5 = new NormalizationStep(raw5, false);
        var result5 = TitleNormalizationUtils.sanitizeFinalOutput(inputStep5);
        Assertions.assertNotNull(result5);
        Assertions.assertTrue(result5.isDone());
        Assertions.assertEquals( "2003",result5.value());


    }
}
