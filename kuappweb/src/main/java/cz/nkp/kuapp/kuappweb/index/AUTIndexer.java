package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.FormatUtils;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.XML;
import org.json.XMLParserConfiguration;

/**
 *
 * @author alber
 */
public class AUTIndexer {

    static final Logger LOGGER = Logger.getLogger(AUTIndexer.class.getName());
    static XMLInputFactory _xmlFactory = XMLInputFactory.newFactory();
    final String OAI_Base = "https://aleph.nkp.cz/OAI";

    public String request(String url) throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(new URI(url))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
    
    public JSONObject full() {
        JSONObject ret = new JSONObject();
        Date start = new Date();
        ret = fromURL(OAI_Base + "?verb=ListRecords&metadataPrefix=marc21&set=AUT");
        Date end = new Date();
        String ellapsed = FormatUtils.formatInterval(end.getTime() - start.getTime());
        ret.put("ellapsed time", ellapsed);
        LOGGER.log(Level.INFO, "Indexing {0} FINISHED. indexed {1}. Time: {2}", new Object[]{"AUT", ret.getInt("aut"), ellapsed});
        return ret;
        
    }
    
    public JSONObject resumptionToken(String rt) {
        
        JSONObject ret = new JSONObject();
        Date start = new Date();
        String url = OAI_Base
                        + "?verb=ListRecords&resumptionToken="
                        + rt;
        ret = fromURL(url);
        Date end = new Date();
        String ellapsed = FormatUtils.formatInterval(end.getTime() - start.getTime());
        ret.put("ellapsed time", ellapsed);
        LOGGER.log(Level.INFO, "Indexing {0} FINISHED. indexed {1}. Time: {2}", new Object[]{"AUT", ret.getInt("aut"), ellapsed});
        return ret;
    }

    private JSONObject fromURL(String url) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder("http://localhost:8983/solr").build()) {
            LOGGER.log(Level.INFO, "Retreiving {0}", url);
            String xml = request(url);
            JSONObject json = XML.toJSONObject(xml, XMLParserConfiguration.ORIGINAL);
            processResponse(xml, json, ret, solr);
            boolean hasResumption = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").optString("resumptionToken") != null;

            while (hasResumption) {
                url = OAI_Base
                        + "?verb=ListRecords&resumptionToken="
                        + json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").getString("resumptionToken");

                LOGGER.log(Level.INFO, "Retreiving {0}", url);
                xml = request(url);
                json = XML.toJSONObject(xml, XMLParserConfiguration.ORIGINAL);
                processResponse(xml, json, ret, solr);
                LOGGER.log(Level.INFO, "Indexing AUT. Current {1}", ret.getInt("aut"));
                hasResumption = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").optString("resumptionToken") != null;  
            } 
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("aut", "error: " + ex);
        }
        return ret;
    }

    private void dataField(SolrInputDocument idoc, JSONObject df, String tag, String code, String field) {
        String fieldname = field == null ? "datafield_" + tag + code : field;
        if (tag.equals(df.opt("tag") + "")) {
            Object sfsO = df.get("marc:subfield");
            if (sfsO instanceof JSONArray) {
                JSONArray sfs = (JSONArray) sfsO;
                for (int s = 0; s < sfs.length(); s++) {
                    if (code.equals(sfs.getJSONObject(s).opt("code") + "")) {
                        idoc.setField(fieldname, sfs.getJSONObject(s).get("content")+"");
                    }
                }
            } else {
                JSONObject o = (JSONObject) sfsO;
                if (code.equals(o.opt("code") + "")) {
                    idoc.setField(fieldname, o.get("content")+"");
                }
            }
            

        }
    }

    private void processResponse(String xml, JSONObject json, JSONObject ret, SolrClient solr) throws SolrServerException, IOException {
        JSONArray records = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").getJSONArray("record");
        List<SolrInputDocument> docs = new ArrayList<>();

        //int i = 0;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.getJSONObject(i);
            if (!record.has("metadata")) {
                continue;
            }
            SolrInputDocument idoc = new SolrInputDocument();
            idoc.setField("id", record.getJSONObject("header").getString("identifier"));
            idoc.setField("datestamp", record.getJSONObject("header").getString("datestamp"));
            idoc.setField("xml", record.toString());
            JSONObject mr = record.getJSONObject("metadata").getJSONObject("marc:record");
            JSONArray cfs = mr.getJSONArray("marc:controlfield");
            for (int c = 0; c < cfs.length(); c++) {
                idoc.setField("controlfield_" + cfs.getJSONObject(c).getString("tag"), cfs.getJSONObject(c).get("content").toString());
            }
            JSONArray dfs = mr.getJSONArray("marc:datafield");
            for (int d = 0; d < dfs.length(); d++) {
                // parse data fields
                JSONObject df = dfs.getJSONObject(d);

                // tag=100 code=a jmeno    
                //dataField(idoc, df, "100", "a", "name");
                // tag=046 code=f datum narozeni, code=g umrti    
                dataField(idoc, df, "046", "f", "narozeni");
                dataField(idoc, df, "046", "g", "umrti");
                dataField(idoc, df, "998", "a", "aleph_link");
                
                dataField(idoc, df, "368", "c", null);
                dataField(idoc, df, "100", "d", null);
                dataField(idoc, df, "100", "a", null);
            }
            docs.add(idoc);
        }

        if (!docs.isEmpty()) {
            solr.add("aut", docs);
            ret.put("aut", docs.size() + "");
        }
        solr.commit("aut");

    }

}
