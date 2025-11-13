
package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
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
    
    public static JSONObject indexLabels(JSONArray js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("id", "labels");
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
    
    public static JSONObject indexDocLabels(JSONObject js) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrInputDocument doc = new SolrInputDocument();
            doc.setField("pid", js.getString("pid"));
            
            
            Map<String,Object> partialUpdate = new HashMap<>();
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
}
