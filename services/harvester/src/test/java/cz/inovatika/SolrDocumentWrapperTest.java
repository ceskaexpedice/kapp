package cz.inovatika;

import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;

import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SolrDocumentWrapperTest {

    public static void main(String[] args) throws SolrServerException, IOException {
        try(SolrClient solrClient = new Http2SolrClient.Builder("http://localhost:8983/solr")
                .withConnectionTimeout(200, TimeUnit.SECONDS)
                .withIdleTimeout(5,TimeUnit.MINUTES)
                .withRequestTimeout(5,TimeUnit.MINUTES).useHttp1_1(true)
                .build()) {
            SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                    Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                    new SolrInputDocument(),
                    SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);

            String randomValue = UUID.randomUUID().toString();

            wrapper.setField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, "uuid:00154d90-ded3-11e5-a3e0-005056827e51");
            wrapper.addField(Constants.TitlesCollectionFields.AppDataFields.LICENSES_FIELD, Arrays.asList(randomValue));
            System.out.println(wrapper.getDocument());


            UpdateRequest updateRequest = new UpdateRequest();
            updateRequest.add(wrapper.getDocument());

            //UpdateResponse response = 
             updateRequest.process(solrClient, "titles");

            solrClient.commit("titles");

        }




    }
}
