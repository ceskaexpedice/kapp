package cz.inovatika.conf;

import cz.inovatika.model.APIVersion;
import cz.inovatika.model.Instance;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Year;
import java.util.List;

public class ConfigurationTest {

    @Test
    public void testLoad() {


        InputStream resourceAsStream = ConfigurationTest.class.getResourceAsStream("app.conf");
        ApplicationConfig config = ApplicationConfig.loadFromStream(resourceAsStream);
        List<Instance> instances = config.instances();
        Assertions.assertNotNull(instances);
        Assertions.assertEquals(1, instances.size());
        Assertions.assertEquals("mzk", instances.getFirst().getAcronym());
        Assertions.assertEquals(APIVersion.v7, instances.getFirst().getApiVersion());
        Assertions.assertEquals("https://kramerius.mzk.cz/search/api/v7.0/client", instances.getFirst().getUrl());

        Assertions.assertNotNull(config.workflow());
        Assertions.assertEquals(110, config.workflow().getPublicYear());
        int calculatePublicYear = config.workflow().calculatePublicYear();
        int expectation = Year.now().minusYears(110).getValue();
        Assertions.assertEquals(expectation, calculatePublicYear);

        Assertions.assertEquals(70, config.workflow().getOnsiteYear());
        int calculateOnsiteYear = config.workflow().calculateOnsiteYear();
        expectation = Year.now().minusYears(70).getValue();
        Assertions.assertEquals(expectation, calculateOnsiteYear);

    }
}
