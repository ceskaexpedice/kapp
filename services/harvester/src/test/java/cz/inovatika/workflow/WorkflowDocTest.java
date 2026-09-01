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
import java.time.Year;
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

    @Test
    void clearsReleaseYearWhenNoYearCanBeCalculatedForAtomicStateChange() {
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );

        new WorkflowDoc(
                null,
                1900,
                States.CONTROLLED_PUBLIC,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                false
        ).mapToSolrDocument(wrapper, "test", workflowConfig());

        Assertions.assertNull(atomicSetValue(wrapper, AppComputedFields.RELEASE_YEAR));
    }

    @Test
    void officialReleaseYearHasPriorityOverSpecialAuthorshipAndAuthorDeath() {
        WorkflowConfig config = workflowConfig();
        SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                AppDataFields.PID_FIELD,
                SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE
        );

        int issuedYear = Year.now().getValue() - 10;
        new WorkflowDoc(
                issuedYear,
                1900,
                States.CONTROLLED_PUBLIC,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                true
        ).mapToSolrDocument(wrapper, "test", config);

        Assertions.assertEquals(issuedYear, atomicSetValue(wrapper, AppComputedFields.RELEASE_YEAR));
    }

    @Test
    void toControlSpecialAuthorshipUsesAuthorDeadEnoughReleaseYearWithoutAuthorDeath() {
        ApplicationConfig config = applicationConfig();
        int issuedYear = Year.now().getValue() - config.workflow().getAuthorDeadEnoughYear() - 1;
        WorkflowDoc doc = new WorkflowDoc(
                issuedYear,
                null,
                States.TO_CONTROL,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                true,
                false,
                false,
                false
        );

        States newState = States.TO_CONTROL.calculateNextState("uuid:test", null, config, doc, new StringWrapper("test"));

        Assertions.assertEquals(States.NONCONTROLLED_PUBLIC, newState);
    }

    @Test
    void officialityBeatsSpecialAuthorshipWhenSpecialReleaseYearIsStillInFuture() {
        ApplicationConfig config = applicationConfig();
        int issuedYear = Year.now().getValue() - config.workflow().getAuthorDeadEnoughYear() + 1;
        WorkflowDoc doc = new WorkflowDoc(
                issuedYear,
                1900,
                States.TO_CONTROL,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                true
        );

        States newState = States.TO_CONTROL.calculateNextState("uuid:test", null, config, doc, new StringWrapper("test"));

        Assertions.assertEquals(States.NONCONTROLLED_PUBLIC, newState);
    }

    @Test
    void officialReleaseYearEqualsDateIssued() {
        ApplicationConfig config = applicationConfig();
        int issuedYear = Year.now().getValue();
        WorkflowDoc doc = new WorkflowDoc(
                issuedYear,
                null,
                States.TO_CONTROL,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                false,
                false,
                true
        );

        Assertions.assertEquals(issuedYear, doc.releaseYear(config.workflow()));
        States newState = States.TO_CONTROL.calculateNextState("uuid:test", null, config, doc, new StringWrapper("test"));
        Assertions.assertEquals(States.NONCONTROLLED_PUBLIC, newState);
    }

    @Test
    void priorityReleaseRuleWithoutDateIssuedDoesNotFallBackToAuthorDeath() {
        WorkflowDoc doc = new WorkflowDoc(
                null,
                1900,
                States.TO_CONTROL,
                "tester",
                ModelFields.MONOGRAPH,
                false,
                false,
                true,
                false,
                false
        );

        Assertions.assertNull(doc.releaseYear(workflowConfig()));
    }

    private static Object atomicSetValue(SolrInputDocumentWrapper wrapper, String field) {
        Object value = wrapper.getDocument().getFieldValue(field);
        Assertions.assertInstanceOf(Map.class, value);
        return ((Map<?, ?>) value).get("set");
    }

    private static WorkflowConfig workflowConfig() {
        return applicationConfig().workflow();
    }

    private static ApplicationConfig applicationConfig() {
        return ApplicationConfig.loadFromStream(new ByteArrayInputStream("""
                {"workflow":{"publicYear":110,"onsiteYear":50,"authorDeadEnoughYear":70}}
                """.getBytes(StandardCharsets.UTF_8)));
    }
}
