package cz.inovatika.impl;

import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.Labels;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.utils.XMLUtils;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HarvesterDateRangeTest {

    @Test
    void parsesImpreciseIssuedDateHintAndLabelsDocument() throws Exception {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.SIMPLE);
        Document document = XMLUtils.parseDocument(
                new StringReader("""
                        <mods:mods xmlns:mods="http://www.loc.gov/mods/v3">
                          <mods:originInfo>
                            <mods:dateIssued>[18--?]</mods:dateIssued>
                          </mods:originInfo>
                        </mods:mods>
                        """),
                true);

        HarvesterImpl.parseDateRange(wrapper, document);

        assertEquals(1899, wrapper.getFieldValue(AppComputedFields.DATE_ISSUED_YEAR));
        assertEquals(Labels.IMPRECISE_DATE_ISSUED, wrapper.getFieldValue(AppComputedFields.LABELS_FIELD));
    }
}
