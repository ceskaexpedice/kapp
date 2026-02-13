package cz.inovatika.cz.inovatika.touchstore;

import cz.inovatika.touchstore.TouchStore;
import cz.inovatika.touchstore.TouchStoreFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class TouchStoreTest {

    @Test
    public void testConfig() throws IOException {
        SolrClient solrClient =null;
        solrClient = new Http2SolrClient.Builder("http://localhost:8983/solr")
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5,TimeUnit.MINUTES)
                .withRequestTimeout(5,TimeUnit.MINUTES).useHttp1_1(true)
                .build();

        TouchStore touchStore = TouchStoreFactory.solr.create(solrClient, null);
        boolean exists = touchStore.exists("uuid:0569d900-243f-11e4-8e0d-005056827e51");
        System.out.println(exists);

        List<String> physicalLocation = touchStore.getPhysicalLocations("uuid:0569d900-243f-11e4-8e0d-005056827e51");
        System.out.println(physicalLocation);

        String md5sum = touchStore.getMd5("uuid:0569d900-243f-11e4-8e0d-005056827e51");
        System.out.println(md5sum);


        solrClient.close();

    }

}
