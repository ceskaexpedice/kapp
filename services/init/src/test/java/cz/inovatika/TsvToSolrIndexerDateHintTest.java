package cz.inovatika;

import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.Labels;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TsvToSolrIndexerDateHintTest {

    @Test
    void parsesImpreciseCenturyHintAsLatestPossibleYear() {
        ParsedDate parsed = parse("[18--?]");

        //assertEquals(1800, parsed.field(AppDataFields.DATE_ISSUED_START_YEAR_FIELD));
        //assertEquals(1899, parsed.field(AppDataFields.DATE_ISSUED_END_YEAR_FIELD));
        assertEquals(1899, parsed.field(AppDataFields.DATE_ISSUED_YEAR_FIELD));
        assertTrue(parsed.labels().contains(Labels.IMPRECISE_DATE_ISSUED));
    }

    @Test
    void parsesTrailingBracketCenturyHint() {
        ParsedDate parsed = parse("18--]");

        //assertEquals(1800, parsed.field(AppDataFields.DATE_ISSUED_START_YEAR_FIELD));
        assertEquals(1899, parsed.field(AppDataFields.DATE_ISSUED_YEAR_FIELD));
        assertTrue(parsed.labels().contains(Labels.IMPRECISE_DATE_ISSUED));
    }

    @Test
    void parsesImpreciseDecadeHintAsLatestPossibleYear() {
        ParsedDate parsed = parse("[192-?]");

        //assertEquals(1920, parsed.field(AppDataFields.DATE_ISSUED_START_YEAR_FIELD));
        //assertEquals(1929, parsed.field(AppDataFields.DATE_ISSUED_END_YEAR_FIELD));
        assertEquals(1929, parsed.field(AppDataFields.DATE_ISSUED_YEAR_FIELD));
        assertTrue(parsed.labels().contains(Labels.IMPRECISE_DATE_ISSUED));
    }

    private static ParsedDate parse(String value) {
        List<String> labels = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        SolrInputDocumentWrapper doc = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.SIMPLE);

        XmlProcessor.parseDateBasic(doc, value, "test", value, labels, notes);
        return new ParsedDate(doc, labels);
    }

    private record ParsedDate(SolrInputDocumentWrapper doc, List<String> labels) {
        int field(String fieldName) {
            return ((Number) doc.getFieldValue(fieldName)).intValue();
        }
    }
}
