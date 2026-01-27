package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    public static JSONObject indexTitleColumns(JSONArray js) {

        JSONObject ret = saveConfig(js, "titleColumns");
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
            partialUpdate.put("set", js.getString("batch"));
            doc.setField("batch", partialUpdate);

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

    public static JSONObject saveDecisionDate(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", js.getString("decisionDate"));
            doc.setField("decision_date", partialUpdate);
            
            solr.add("titles", doc);
            solr.commit("titles");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    // Save changes to author
    public static JSONObject saveAuthor(JSONObject js) {

        JSONObject ret = new JSONObject(); 
//        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
//            SolrInputDocument doc = new SolrInputDocument();
//            doc.setField("pid", js.getString("pid"));
//
//            Map<String, Object> partialUpdate = new HashMap<>();
//            partialUpdate.put("set", js.getString("decisionDate"));
//            doc.setField("decision_date", partialUpdate);
//            
//            solr.add("titles", doc);
//            solr.commit("titles");
//            ret.put("msg", "saved");
//
//        } catch (Exception ex) {
//            LOGGER.log(Level.SEVERE, null, ex);
//            ret.put("error", ex);
//        }
        return ret;
    }
    
    public static JSONObject addDocToBatch(String pid, String batch) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            ret = addDocToBatch(solr, pid, batch);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject addDocToBatch(SolrClient solr, String pid, String batch) {

        JSONObject ret = new JSONObject();
        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", pid);

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", batch);
            doc.setField("batch", partialUpdate);

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

    public static JSONObject createBatchFromLabel(JSONObject js) {

        JSONObject ret = new JSONObject();
        String label = js.getString("label");
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray docs = IndexSearcher.getLabelDocs(label).getJSONObject("response").getJSONArray("docs");
            for (int i = 0; i < docs.length(); i++) {
                addDocToBatch(solr, docs.getJSONObject(i).getString("pid"), "label_" + label);
            }
            ret.put("batch", "label_" + label);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject changeStateInBatch(JSONObject js) {

        JSONObject ret = new JSONObject();

        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JSONArray docs = IndexSearcher.getBatchDocs(js.getString("batch"), true).getJSONObject("response").getJSONArray("docs");
            for (int i = 0; i < docs.length(); i++) {
                changeDocStateBatch(solr, js, docs.getJSONObject(i).getString("pid"));
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    private static void changeDocStateBatch(SolrClient solr, JSONObject js, String pid) {

        try {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", pid);

            Map<String, Object> partialUpdate = new HashMap<>();
            partialUpdate.put("set", js.getString("state"));
            doc.setField("state", partialUpdate);

            Map<String, Object> licenses = new HashMap<>();
            licenses.put("set", js.getJSONArray("licenses"));
            doc.setField("licenses", licenses);

            Map<String, Object> reason = new HashMap<>();
            reason.put("set", js.getString("reason"));
            doc.setField("state_reason", reason);

            Map<String, Object> user = new HashMap<>();
            reason.put("set", "test");
            doc.setField("state_user", user);

            String d = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

            Map<String, Object> date = new HashMap<>();
            reason.put("set", d);
            doc.setField("state_date", date);

            JSONObject h = new JSONObject()
                    .put("date", d)
                    .put("reason", js.getString("reason"))
                    .put("licenses", js.getJSONArray("licenses"))
                    .put("user", "test")
                    .put("state", js.getString("state"));

            Map<String, Object> state_history = new HashMap<>();
            state_history.put("add", h.toString());
            doc.addField("state_history", state_history);

            solr.add("titles", doc);
            solr.commit("titles");
            LOGGER.log(Level.INFO, "state for {0} changed", pid);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
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
    
    public static JSONObject saveContract(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("name", js.optString("name"));
            doc.setField("title", js.optString("title"));
            doc.setField("filename", js.optString("filename"));
            doc.setField("cislo_smlouvy", js.optString("cislo_smlouvy"));
            doc.setField("nazev_poskytovatele", js.optString("nazev_poskytovatele"));
            doc.setField("ico", js.optString("ico"));
            doc.setField("datum_uzavreni", js.optString("datum_uzavreni"));
            doc.setField("datum_platnosti", js.optString("datum_platnosti"));
            doc.setField("kontaktni_osoby", js.optString("kontaktni_osoby"));
            doc.setField("souhrn", js.optString("souhrn"));
            doc.setField("digitalni_knihovny", js.optString("digitalni_knihovny"));
            doc.setField("hlavni_smlouva", js.optString("hlavni_smlouva"));
            doc.setField("hlavni", js.optBoolean("hlavni"));
            doc.setField("dodatky", js.optString("dodatky"));
  
            solr.add("contracts", doc);
            solr.commit("contracts");
            ret.put("msg", "saved");

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
}
