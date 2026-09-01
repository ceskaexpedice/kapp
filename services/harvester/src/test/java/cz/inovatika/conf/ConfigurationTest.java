package cz.inovatika.conf;

import cz.inovatika.model.APIVersion;
import cz.inovatika.model.Instance;
import cz.inovatika.touchstore.TouchStoreFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.time.Year;
import java.util.List;

public class ConfigurationTest {

    @Test
    public void testLoad() throws IOException {
        ApplicationConfig config;
        try (InputStream resourceAsStream = getClass().getResourceAsStream("app.conf")) {
            Assertions.assertNotNull(resourceAsStream, "Missing test configuration resource");
            config = ApplicationConfig.loadFromStream(resourceAsStream);
        }
        //TouchStoreConfig touchStoreConfig = config.touchStoreConfig();
        TouchStoreFactory factory = config.touchStoreConfig().factory();
        Assertions.assertNotNull(factory);
        Assertions.assertEquals("solr", factory.name());

        List<Instance> instances = config.instances();
        Assertions.assertNotNull(instances);
        Assertions.assertEquals(2, instances.size());
        Instance mzk = instances.stream()
                .filter(instance -> "mzk".equals(instance.getAcronym()))
                .findFirst()
                .orElseThrow();
        Assertions.assertEquals(APIVersion.v7, mzk.getApiVersion());
        Assertions.assertEquals("https://api.kramerius.mzk.cz/search/api/client/v7.0", mzk.getUrl());

        Assertions.assertNotNull(config.workflow());
        Assertions.assertEquals(110, config.workflow().getPublicYear());
        int calculatePublicYear = config.workflow().calculatePublicYear();
        int expectation = Year.now().minusYears(110).getValue();
        Assertions.assertEquals(expectation, calculatePublicYear);

        Assertions.assertEquals(50, config.workflow().getOnsiteYear());
        int calculateOnsiteYear = config.workflow().calculateOnsiteYear();
        expectation = Year.now().minusYears(50).getValue();
        Assertions.assertEquals(expectation, calculateOnsiteYear);

        Assertions.assertEquals(70, config.workflow().getAuthorDeadEnoughYear());
        int calculateAuthorDeadYear = config.workflow().calculateAuthorDeadYear();
        expectation = Year.now().minusYears(70).getValue();
        Assertions.assertEquals(expectation, calculateAuthorDeadYear);

    }
}
