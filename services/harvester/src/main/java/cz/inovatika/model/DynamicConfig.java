package cz.inovatika.model;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.common.util.NamedList;
import org.json.JSONObject;

/**
 * Load config stored in index.
 * This config is managed in web UI
 */
public class DynamicConfig {

    public static final Logger LOGGER = Logger.getLogger(DynamicConfig.class.getName());
    private final String SOLR_URL = "http://localhost:8983/solr";

    public JSONObject loadConfig() {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(SOLR_URL).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .returnFields("id,value:[json]")
                    .setLimit(1);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "configuration");
            InputStream is = (InputStream) resp.get("stream");
            ret.put("config", new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getJSONArray("docs"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
}
