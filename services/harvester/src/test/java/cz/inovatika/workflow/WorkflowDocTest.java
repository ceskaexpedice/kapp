package cz.inovatika.workflow;

import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.ModelFields;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class WorkflowDocTest {

    @Test
    void mapsReleaseYearToAtomicStateChange() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );

        new WorkflowDoc(
                1930,
                1980,
                States.CONTROLLED_PUBLIC,
                "tester",
                ModelFields.MONOGRAPH,
                false
        ).mapToSolrDocument(wrapper, "test", workflowConfig());

        Assertions.assertEquals(Map.of("set", 2040), wrapper.getDocument().getFieldValue(AppComputedFields.RELEASE_YEAR));
    }

    @Test
    void omitsReleaseYearWhenNoYearCanBeCalculatedForSimpleDocument() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.SIMPLE
        );

        new WorkflowDoc(
                null,
                null,
                States.CONTROLLED_PUBLIC,
                "tester",
                ModelFields.MONOGRAPH,
                false
        ).mapToSolrDocument(wrapper, "test", workflowConfig());

        Assertions.assertFalse(wrapper.getDocument().getFieldNames().contains(AppComputedFields.RELEASE_YEAR));
    }

    private static WorkflowConfig workflowConfig() {
        return ApplicationConfig.loadFromStream(new ByteArrayInputStream("""
                {"workflow":{"publicYear":110,"onsiteYear":50,"authorDeadEnoughYear":70}}
                """.getBytes(StandardCharsets.UTF_8))).workflow();
    }
}
