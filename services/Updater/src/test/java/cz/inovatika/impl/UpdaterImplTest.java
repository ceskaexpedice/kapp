package cz.inovatika.impl;

import cz.inovatika.Constants.TitlesCollectionFields.AppComputedFields;
import cz.inovatika.Constants.TitlesCollectionFields.AppDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.ModelFields;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.solr.SolrInputDocumentWrapper;
import cz.inovatika.workflow.States;
import cz.inovatika.workflow.WorkflowDoc;
import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

class UpdaterImplTest {

    @Test
    void updatesReleaseYearWhenPriorityRuleChangesItWithoutStateTransition() {
        WorkflowConfig config = workflowConfig();
        SolrDocument source = new SolrDocument();
        source.setField(AppComputedFields.RELEASE_YEAR, 2000);
        SolrInputDocumentWrapper target = atomicDocument("uuid:test");
        WorkflowDoc workflowDoc = new WorkflowDoc(
                1930,
                1900,
                States.NONCONTROLLED_PRIVATE,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                false
        );

        boolean changed = UpdaterImpl.updateReleaseYearIfNeeded(source, target, workflowDoc, config);

        Assertions.assertTrue(changed);
        Assertions.assertEquals(1930 + config.getOnsiteYear(), atomicSetValue(target, AppComputedFields.RELEASE_YEAR));
    }

    @Test
    void clearsReleaseYearWhenPriorityRuleCannotCalculateOneWithoutDateIssued() {
        SolrDocument source = new SolrDocument();
        source.setField(AppComputedFields.RELEASE_YEAR, 2000);
        SolrInputDocumentWrapper target = atomicDocument("uuid:test");
        WorkflowDoc workflowDoc = new WorkflowDoc(
                null,
                1900,
                States.NONCONTROLLED_PRIVATE,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                false
        );

        boolean changed = UpdaterImpl.updateReleaseYearIfNeeded(source, target, workflowDoc, workflowConfig());

        Assertions.assertTrue(changed);
        Assertions.assertNull(atomicSetValue(target, AppComputedFields.RELEASE_YEAR));
    }

    @Test
    void skipsReleaseYearWhenAlreadyCorrect() {
        WorkflowConfig config = workflowConfig();
        SolrDocument source = new SolrDocument();
        source.setField(AppComputedFields.RELEASE_YEAR, 1930 + config.getOnsiteYear());
        SolrInputDocumentWrapper target = atomicDocument("uuid:test");
        WorkflowDoc workflowDoc = new WorkflowDoc(
                1930,
                1900,
                States.NONCONTROLLED_PRIVATE,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                false
        );

        boolean changed = UpdaterImpl.updateReleaseYearIfNeeded(source, target, workflowDoc, config);

        Assertions.assertFalse(changed);
        Assertions.assertFalse(target.getDocument().getFieldNames().contains(AppComputedFields.RELEASE_YEAR));
    }

    private static SolrInputDocumentWrapper atomicDocument(String pid) {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );
        wrapper.setField(AppDataFields.PID_FIELD, pid);
        return wrapper;
    }

    private static Object atomicSetValue(SolrInputDocumentWrapper wrapper, String field) {
        Object value = wrapper.getDocument().getFieldValue(field);
        Assertions.assertInstanceOf(Map.class, value);
        return ((Map<?, ?>) value).get("set");
    }

    private static WorkflowConfig workflowConfig() {
        return ApplicationConfig.loadFromStream(new ByteArrayInputStream("""
                {"workflow":{"publicYear":110,"onsiteYear":50,"authorDeadEnoughYear":70}}
                """.getBytes(StandardCharsets.UTF_8))).workflow();
    }
}
