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
        Assertions.assertTrue(instances.size() == 1);
        Assertions.assertTrue(instances.get(0).getAcronym().equals("mzk"));
        Assertions.assertTrue(instances.get(0).getApiVersion().equals(APIVersion.v7));
        Assertions.assertTrue(instances.get(0).getUrl().equals("https://kramerius.mzk.cz/search/api/v7.0/client"));

        Assertions.assertNotNull(config.getWorkflow());
        Assertions.assertTrue(config.getWorkflow().getPublicYear() == 110);
        int calculatePublicYear = config.getWorkflow().calculatePublicYear();
        int expecationp = Year.now().minusYears(110).getValue();
        Assertions.assertEquals(expecationp, calculatePublicYear);

        Assertions.assertTrue(config.getWorkflow().getOnsiteYear() == 70);
        int calculateOnsiteYear = config.getWorkflow().calculateOnsiteYear();
        int expecationo = Year.now().minusYears(70).getValue();
        Assertions.assertEquals(expecationo, calculateOnsiteYear);

        System.out.println(expecationo);
    }
}
