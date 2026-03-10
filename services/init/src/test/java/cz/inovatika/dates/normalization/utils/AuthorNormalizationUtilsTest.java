package cz.inovatika.dates.normalization.utils;

import cz.inovatika.dates.normalization.NormalizationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


public class AuthorNormalizationUtilsTest {




    @Test
    public void testLifeYears() {
        String raw1 = "nar. 1913";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = AuthorNormalizationUtils.extractLifeYears(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals("1913-", result1.value());

        String raw2 = "zemr 1982";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = AuthorNormalizationUtils.extractLifeYears(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals("-1982", result2.value());

        String raw3 = "zem 1982";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = AuthorNormalizationUtils.extractLifeYears(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals("-1982", result3.value());

        String raw4 = "zemr. 488";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = AuthorNormalizationUtils.extractLifeYears(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals("-488", result4.value());

        String raw5 = "nar. ca 1800";
        var inputStep5 = new NormalizationStep(raw5, false);
        var result5 = AuthorNormalizationUtils.extractLifeYears(inputStep5);
        Assertions.assertNotNull(result5);
        Assertions.assertTrue(result5.isDone());
        Assertions.assertEquals("1800-", result5.value());
    }

    @Test
    public void testActiveYears() {
        String raw3 = "cinny kolem r. 1815";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = AuthorNormalizationUtils.extractActiveYears(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals("1815", result3.value());

        String raw4 = "cinny 1799 az 1808";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = AuthorNormalizationUtils.extractActiveYears(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals("1799-1808", result4.value());
    }


    @Test
    public void testYear() {
        String raw1 = " 1879";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = AuthorNormalizationUtils.year(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals("1879", result1.value());

        String raw2 = "asi 1879";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = AuthorNormalizationUtils.year(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals("1879", result2.value());

        String raw3 = "ca 1879";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = AuthorNormalizationUtils.year(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals("1879", result3.value());

        String raw4 = "asi 1879";
        var inputStep4 = new NormalizationStep(raw4, false);
        var result4 = AuthorNormalizationUtils.year(inputStep4);
        Assertions.assertNotNull(result4);
        Assertions.assertTrue(result4.isDone());
        Assertions.assertEquals("1879", result4.value());
    }

    @Test
    public void testRange() {
        //1499-ca 1542
        String raw1 = "1499-ca 1542";
        var inputStep1 = new NormalizationStep(raw1, false);
        var result1 = AuthorNormalizationUtils.range(inputStep1);
        Assertions.assertNotNull(result1);
        Assertions.assertTrue(result1.isDone());
        Assertions.assertEquals("1499-1542", result1.value());

    }

    @Test
    public void testCloseRange() {
        //-asi 1502
        String raw2 = "-asi 1502";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = AuthorNormalizationUtils.closeRange(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
        Assertions.assertEquals("-1502", result2.value());

        String raw3 = "-asi 66";
        var inputStep3 = new NormalizationStep(raw3, false);
        var result3 = AuthorNormalizationUtils.closeRange(inputStep3);
        Assertions.assertNotNull(result3);
        Assertions.assertTrue(result3.isDone());
        Assertions.assertEquals("-66", result3.value());

    }

    @Test
    public void testOpenRange() {
        //-asi 1502
        String raw2 = "1938-";
        var inputStep2 = new NormalizationStep(raw2, false);
        var result2 = AuthorNormalizationUtils.openRange(inputStep2);
        Assertions.assertNotNull(result2);
        Assertions.assertTrue(result2.isDone());
    }

}