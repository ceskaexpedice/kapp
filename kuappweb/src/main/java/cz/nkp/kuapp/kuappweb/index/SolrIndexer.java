package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class SolrIndexer {

    public static final Logger LOGGER = Logger.getLogger(SolrIndexer.class.getName());

    public static JSONObject saveConfig(JSONArray js, String id) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("id", id);
            doc.setField("value", js.toString());
            solr.add("configuration", doc);
            solr.commit("configuration");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject indexLabels(JSONArray js) {

        JSONObject ret = saveConfig(js, "labels");
        return ret;
    }

    public static JSONObject indexDocLabels(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", js.getJSONArray("labels"));
            doc.setField("labels", partialUpdate);

            // doc.setField("labels", js.getJSONArray("labels").toString());
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    

    public static JSONObject indexBatches(JSONArray js) {

        JSONObject ret = saveConfig(js, "batches");
        return ret;
    }

    public static JSONObject indexDocBatches(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", js.getJSONArray("batches"));
            doc.setField("batches", partialUpdate);

            // doc.setField("labels", js.getJSONArray("labels").toString());
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject addDocToBatch(String pid, String batch) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", pid);

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", batch);
            doc.setField("batches", partialUpdate);

            // doc.setField("labels", js.getJSONArray("labels").toString());
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject changeState(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", js.getString("state"));
            doc.setField("state", partialUpdate);

            Map<String, Object> licenses = new HashMap<>();
            licenses.put("set", js.getJSONArray("licenses"));
            doc.setField("licenses", licenses);

            Map<String, Object> reason = new HashMap<>();
            reason.put("set", js.getString("reason"));
            doc.setField("reason", reason);

            JSONObject h = new JSONObject()
                    .put("date", LocalDateTime.now().toString())
                    .put("reason", js.getString("reason"))
                    .put("licenses", js.getJSONArray("licenses"))
                    .put("user", "test")
                    .put("state", js.getString("state"));

            Map<String, Object> state_history = new HashMap<>();
            state_history.put("add", h.toString());
            doc.addField("state_history", state_history);

            solr.add("titles", doc);
            solr.commit("titles");
            LOGGER.log(Level.INFO, "state for {0} changed", js.getString("pid"));
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
}
