package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.FormatUtils;
import cz.nkp.kuapp.kuappweb.Options;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLInputFactory;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.common.SolrDocumentList;
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
        LOGGER.log(Level.INFO, "Indexing {0} FINISHED. indexed {1}. Time: {2}", new Object[]{"AUT", ret.opt("aut"), ellapsed});
        return ret;
    }

    private String getLastDateStamp() {
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrQuery query = new SolrQuery()
                    .setQuery("*")
                    .setSort(SolrQuery.SortClause.desc("datestamp"))
                    .setFields("datestamp")
                    .setRows(1);

            SolrDocumentList docs = solr.query("aut", query).getResults();
            if (docs.getNumFound() > 0) {
                return ((Date) docs.get(0).getFirstValue("datestamp")).toInstant().toString();
            } else {
                return null;
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            return null;
        }
    }

    public JSONObject update() {
        Date start = new Date();
        String from = getLastDateStamp();
        String url = OAI_Base + "?verb=ListRecords&metadataPrefix=marc21&set=AUT";
        if (from != null) {
            url += "&from=" + from;
        }
        JSONObject ret = fromURL(url);
        Date end = new Date();
        String ellapsed = FormatUtils.formatInterval(end.getTime() - start.getTime());
        ret.put("ellapsed time", ellapsed);
        LOGGER.log(Level.INFO, "Indexing {0} FINISHED. indexed {1}. Time: {2}", new Object[]{"AUT", ret.opt("aut"), ellapsed});
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
        LOGGER.log(Level.INFO, "Indexing {0} FINISHED. indexed {1}. Time: {2}", new Object[]{"AUT", ret.opt("aut"), ellapsed});
        return ret;
    }

    private JSONObject fromURL(String url) {
        JSONObject ret = new JSONObject();
        ret.put("aut", 0);
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            LOGGER.log(Level.INFO, "Retreiving {0}", url);
            String xml = request(url);
            JSONObject json = XML.toJSONObject(xml, XMLParserConfiguration.ORIGINAL);
            processResponse(xml, json, ret, solr);
            boolean hasResumption = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").optString("resumptionToken", null) != null;

            while (hasResumption) {
                url = OAI_Base
                        + "?verb=ListRecords&resumptionToken="
                        + json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").getString("resumptionToken");

                LOGGER.log(Level.INFO, "Retreiving {0}", url);
                xml = request(url);
                json = XML.toJSONObject(xml, XMLParserConfiguration.ORIGINAL);
                processResponse(xml, json, ret, solr);
                LOGGER.log(Level.INFO, "Indexing AUT. Current {0}", ret.getInt("aut"));
                hasResumption = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords").optString("resumptionToken", null) != null;
                Thread.sleep(100);
                //hasResumption = false;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex.toString());
        }
        return ret;
    }

    private void dataField(SolrInputDocument idoc, JSONObject df, String tag, String code, String ind, String field) {
        String fieldname = field == null ? "datafield_" + tag + code : field;
        if (tag.equals(df.opt("tag") + "") && (ind == null || ind.equals(df.opt("ind1") + ""))) {
            Object sfsO = df.get("marc:subfield");
            if (sfsO instanceof JSONArray) {
                JSONArray sfs = (JSONArray) sfsO;
                for (int s = 0; s < sfs.length(); s++) {
                    JSONObject sf = sfs.getJSONObject(s);
                    if (code.equals(sf.opt("code") + "")) {
                        idoc.setField(fieldname, sf.get("content") + "");
                    }
                }
            } else {
                JSONObject o = (JSONObject) sfsO;
                if (code.equals(o.opt("code") + "")) {
                    idoc.setField(fieldname, o.get("content") + "");
                }
            }
        }
    }

    private SolrInputDocument doSolrInputDocument(JSONObject record) {
        if (!record.has("metadata")) {
            return null;
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
            dataField(idoc, df, "046", "f", null, "narozeni");
            dataField(idoc, df, "046", "g", null, "umrti");
            dataField(idoc, df, "998", "a", null, "aleph_link");

            dataField(idoc, df, "368", "c", null, null);
            dataField(idoc, df, "100", "d", null, null);
            dataField(idoc, df, "100", "a", "0", "df1000a");
            dataField(idoc, df, "100", "a", "1", "df1001a");
            dataField(idoc, df, "100", "a", null, null);
        }
        if (idoc.containsKey("df1001a")) {
            String[] p = ((String) idoc.getFieldValue("df1001a")).split(",");
            idoc.setField("family", p[0].trim());
            if (p.length > 1) {
                idoc.setField("given", p[1].trim());
            }
        }
        return idoc;
    }

    private void processResponse(String xml, JSONObject json, JSONObject ret, SolrClient solr) throws SolrServerException, IOException {
        JSONObject verb = json.getJSONObject("OAI-PMH").getJSONObject("ListRecords");
        if (!verb.has("record")) {
            return;
        }
        JSONArray records = verb.optJSONArray("record");
        List<SolrInputDocument> docs = new ArrayList<>();

        if (records == null) {
            // Jenom jeden
            JSONObject record = verb.getJSONObject("record");
            SolrInputDocument idoc = doSolrInputDocument(record);
            if (idoc != null) {
                docs.add(idoc);
            }
        }

        //int i = 0;
        if (records != null) {
            for (int i = 0; i < records.length(); i++) {
                JSONObject record = records.getJSONObject(i);
                SolrInputDocument idoc = doSolrInputDocument(record);
                if (idoc != null) {
                    docs.add(idoc);
                }
            }
        }

        if (!docs.isEmpty()) {
            solr.add("aut", docs);
            ret.put("aut", ret.optInt("aut", 0) + docs.size());
        }
        solr.commit("aut");

    }

}
