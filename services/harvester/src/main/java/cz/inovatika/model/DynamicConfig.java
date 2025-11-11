package cz.inovatika.model;

import java.io.InputStream;

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

    public JSONObject loadConfig() {
        JSONObject ret = new JSONObject();
        // try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
        //     JsonQueryRequest jrequest = new JsonQueryRequest()
        //             .setQuery("*")
        //             .returnFields("id,value:[json]")
        //             .setLimit(1);

        //     jrequest.setResponseParser(new InputStreamResponseParser("json"));
        //     NamedList<Object> resp = solr.request(jrequest, "configuration");
        //     InputStream is = (InputStream) resp.get("stream");
        //     ret.put("config", new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getJSONArray("docs"));

        // } catch (Exception ex) {
        //     LOGGER.log(Level.SEVERE, null, ex);
        //     ret.put("error", ex);
        // }
        return ret;
    }
}
