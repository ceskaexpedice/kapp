package cz.inovatika.conf;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

class WorkflowConfigTest {

    @Test
    void releaseYearUsesEarlierPublicOrAuthorReleaseYear() {
        WorkflowConfig config = workflowConfig();

        Assertions.assertEquals(2040, config.calculateReleaseYear(1930, 1980));
        Assertions.assertEquals(2020, config.calculateReleaseYear(1930, 1950));
        Assertions.assertEquals(2010, config.calculateReleaseYear(1900, 2020));
    }

    @Test
    void releaseYearRespectsOnsiteFloorForAuthorReleaseYear() {
        WorkflowConfig config = workflowConfig();

        Assertions.assertEquals(1980, config.calculateReleaseYear(1930, 1900));
    }

    @Test
    void releaseYearIgnoresMissingInputs() {
        WorkflowConfig config = workflowConfig();

        Assertions.assertEquals(2040, config.calculateReleaseYear(1930, null));
        Assertions.assertEquals(2050, config.calculateReleaseYear(null, 1980));
        Assertions.assertNull(config.calculateReleaseYear(null, null));
    }

    private static WorkflowConfig workflowConfig() {
        return ApplicationConfig.loadFromStream(new ByteArrayInputStream("""
                {"workflow":{"publicYear":110,"onsiteYear":50,"authorDeadEnoughYear":70}}
                """.getBytes(StandardCharsets.UTF_8))).workflow();
    }
}
