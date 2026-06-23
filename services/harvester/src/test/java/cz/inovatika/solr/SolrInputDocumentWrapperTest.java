package cz.inovatika.solr;

import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class SolrInputDocumentWrapperTest {

    @Test
    void titleAtomicChangeSetsLastModifiedDateAsAtomicSet() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );

        wrapper.setField(AppDataFields.PID_FIELD, "uuid:test");
        wrapper.setField(AppDataFields.TITLE_FIELD, "Changed title");

        Object value = wrapper.getDocument().getFieldValue(AppComputedFields.LAST_MODIFIED_DATE);
        Assertions.assertInstanceOf(Map.class, value);
        Assertions.assertNotNull(((Map<?, ?>) value).get("set"));
    }

    @Test
    void titleSimpleChangeSetsLastModifiedDateAsPlainValue() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.SIMPLE
        );

        wrapper.setField(AppDataFields.PID_FIELD, "uuid:test");
        wrapper.setField(AppDataFields.TITLE_FIELD, "Changed title");

        Object value = wrapper.getDocument().getFieldValue(AppComputedFields.LAST_MODIFIED_DATE);
        Assertions.assertInstanceOf(String.class, value);
    }

    @Test
    void nonTitleWrapperDoesNotSetLastModifiedDateAutomatically() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                "nameIdentifier",
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );

        wrapper.setField("nameIdentifier", "jk01021023");
        wrapper.setField("full", "Karel Capek");

        Assertions.assertNull(wrapper.getDocument().getFieldValue(AppComputedFields.LAST_MODIFIED_DATE));
    }
}
