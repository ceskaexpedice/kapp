package cz.inovatika.impl;

import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarvesterLicensesTest {

    @Test
    void replacesLicensesForTheHarvestedLibrary() {
        SolrInputDocumentWrapper update = atomicUpdate();

        HarvesterImpl.setHarvestedLicenses(update, "nkp", new String[]{"public", "onsite"});

        assertEquals(Map.of("set", List.of("public", "onsite")),
                update.getDocument().getFieldValue("licenses_nkp"));
        assertFalse(update.getDocument().containsKey(AppDataFields.LICENSES_FIELD));
    }

    @Test
    void clearsLicensesForTheHarvestedLibraryWhenNoneRemain() {
        SolrInputDocumentWrapper update = atomicUpdate();

        HarvesterImpl.setHarvestedLicenses(update, "nkp", new String[0]);

        Map<?, ?> operation = (Map<?, ?>) update.getDocument().getFieldValue("licenses_nkp");
        assertTrue(operation.containsKey("set"));
        assertNull(operation.get("set"));
    }

    @Test
    void combinesDistinctLicensesFromAllConfiguredLibraryFields() {
        SolrDocument source = new SolrDocument();
        source.setField("licenses_nkp", List.of("public", "onsite"));
        source.setField("licenses_mzk", List.of("onsite", "dnnt"));
        source.setField("licenses_unconfigured", List.of("ignored"));

        assertEquals(List.of("public", "onsite", "dnnt"),
                HarvesterImpl.combinedLicenses(source, List.of("licenses_nkp", "licenses_mzk")));
    }

    private static SolrInputDocumentWrapper atomicUpdate() {
        SolrInputDocumentWrapper update = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );
        update.setField(AppDataFields.PID_FIELD, "uuid:test");
        return update;
    }
}
