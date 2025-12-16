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
        List<Instance> instances = config.getInstances();
        Assertions.assertNotNull(instances);
        Assertions.assertEquals(1, instances.size());
        Assertions.assertEquals("mzk", instances.getFirst().getAcronym());
        Assertions.assertEquals(APIVersion.v7, instances.getFirst().getApiVersion());
        Assertions.assertEquals("https://kramerius.mzk.cz/search/api/v7.0/client", instances.getFirst().getUrl());

        Assertions.assertNotNull(config.getWorkflow());
        Assertions.assertEquals(110, config.getWorkflow().getPublicYear());
        int calculatePublicYear = config.getWorkflow().calculatePublicYear();
        int expecationp = Year.now().minusYears(110).getValue();
        Assertions.assertEquals(expecationp, calculatePublicYear);

        Assertions.assertEquals(70, config.getWorkflow().getOnsiteYear());
        int calculateOnsiteYear = config.getWorkflow().calculateOnsiteYear();
        int expecationo = Year.now().minusYears(70).getValue();
        Assertions.assertEquals(expecationo, calculateOnsiteYear);

    }
}
