package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.request.json.TermsFacetMap;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class IndexSearcher {

    public static final Logger LOGGER = Logger.getLogger(IndexSearcher.class.getName());
    final static SimpleDateFormat dtformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static JSONObject home(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String q = "*";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .setLimit(0);
            jrequest.setResponseParser(new InputStreamResponseParser("json"));
                
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            
            ret.put("titles", (new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getInt("numFound"))); 
            
            resp = solr.request(jrequest, "authors");
            is = (InputStream) resp.get("stream");
            ret.put("authors", (new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getInt("numFound"))); 
            

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject searchTitles(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            if (request.getParameter("export") != null) {
                rows = 1000; 
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset")); 
            }
            
            String sort = "indextime desc";
            if (request.getParameter("sort") != null) {
                sort = request.getParameter("sort");
            }
            String q = "*";
            if (request.getParameter("q") != null) {
                String reqQ = request.getParameter("q");
                if (!reqQ.startsWith("\"")) {
                    reqQ = ClientUtils.escapeQueryChars(reqQ);
                }
                q = "{!boost b=query($modelboost)}" + reqQ;
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("df", "full_text") 
//                    .withFacet("authors", new TermsFacetMap("authors")
//                            .setLimit(1000)
//                            .setMinCount(1))
                    .withParam("fq", "{!collapse field=root.pid}") 
                    .withParam("expand", true)
                    .withParam("expand.rows", 100)
                    .withParam("modelboost", "model:monograph") 
                    .withFacet("public", new TermsFacetMap("public")
                            .setLimit(10)
                            .setMinCount(1))
                    .withFacet("labels", new TermsFacetMap("labels")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("authors", new TermsFacetMap("authors")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("nameIdentifier", new TermsFacetMap("nameIdentifier")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("assigned_license", new TermsFacetMap("assigned_license")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("licenses", new TermsFacetMap("licenses")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("state",
                            new TermsFacetMap("state")
                            .setLimit(20)
                            .setSort("index asc")
                            .setMinCount(1))
                    .withFacet("model", new TermsFacetMap("model")
                            .setLimit(20)
                            .setMinCount(1))
                    .withFacet("physical_locations", new TermsFacetMap("physical_locations.facet")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("contracts", new TermsFacetMap("contracts")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("date", new TermsFacetMap("date_range_start.year")
                            .setLimit(20)
                            .setMinCount(1))
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields("*,authors_kramerius:[json],state_history:[json],release_date:sum(decision_date, if(or(eq (model,'monograph'),eq (model,'monographunit')),71,101))")
                    .setLimit(rows);


            if (request.getParameter("model") != null) {
                jrequest = jrequest.withFilter("model:" + request.getParameter("model"));
            } else {
                jrequest = jrequest.withFilter("model:(monograph OR periodical)");
            }


            if (request.getParameter("author") != null) {
                jrequest = jrequest.withFilter("{!tag=ffauthors}authors:(\"" + String.join("\" OR \"", request.getParameterValues("author")) + "\")");
            }

            if (request.getParameter("state") != null) {
                jrequest = jrequest.withFilter("state:" + request.getParameter("state"));
            }

            if (request.getParameter("labels") != null) {
                jrequest = jrequest.withFilter("labels:" + request.getParameter("labels"));
            }

            if (request.getParameter("contracts") != null) {
                jrequest = jrequest.withFilter("contracts:" + request.getParameter("contracts"));
            }

            if (request.getParameter("licenses") != null) {
                jrequest = jrequest.withFilter("licenses:" + request.getParameter("licenses"));
            }

            if (request.getParameter("digital_library") != null) {
                jrequest = jrequest.withFilter("digital_library:" + request.getParameter("digital_library"));
            }
            if (request.getParameter("assigned_license") != null) {
                jrequest = jrequest.withFilter("assigned_license:" + request.getParameter("assigned_license"));
            }

            if (request.getParameter("physical_locations") != null) {
                jrequest = jrequest.withFilter("{!tag=fffl}physical_locations.facet:(\"" + String.join("\" OR \"", request.getParameterValues("physical_locations")) + "\")");
            }

            if (request.getParameter("date") != null) {
                jrequest = jrequest.withFilter("date_range_start.year:" + request.getParameter("date"));
            }

            if (request.getParameter("export") != null) {
                jrequest.setResponseParser(new InputStreamResponseParser("csv"));
            } else {
                jrequest.setResponseParser(new InputStreamResponseParser("json"));
            }
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            
            if (request.getParameter("export") != null) { 
                ret.put("csv",IOUtils.toString(is, "UTF-8")); 
            } else {
                ret = new JSONObject(IOUtils.toString(is, "UTF-8")); 
            }
            

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject searchTitlesByContract(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset")); 
            }
            
            String sort = "indextime desc";
            if (request.getParameter("sort") != null) {
                sort = request.getParameter("sort");
            }
            String q = "*";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("fq", "{!collapse field=root.pid}") 
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields("*,authors_kramerius:[json],state_history:[json],release_date:sum(decision_date, if(or(eq (model,'monograph'),eq (model,'monographunit')),71,101))")
                    .setLimit(rows);


            if (request.getParameter("contract") != null) {
                jrequest = jrequest.withFilter("contracts:(\"" + request.getParameter("contract") + "\")");
            }
            jrequest.setResponseParser(new InputStreamResponseParser("json"));
                
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            
            ret = new JSONObject(IOUtils.toString(is, "UTF-8")); 
            

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject findTitlesByContract(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset")); 
            }
            
            String sort = "indextime desc";
            if (request.getParameter("sort") != null) {
                sort = request.getParameter("sort");
            }
            String q = "*";
            String fl = "*,authors_kramerius:[json],state_history:[json],release_date:sum(decision_date, if(or(eq (model,'monograph'),eq (model,'monographunit')),71,101))";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("fq", "{!join from=ccnb fromIndex=contracts to=id_ccnb}filename:\""+request.getParameter("contract")+"\"") 
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields(fl)
                    .setLimit(rows);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
                
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            
            ret.put("ccnb", (new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getJSONArray("docs"))); 
            
            jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("fq", "{!join from=issn fromIndex=contracts to=id_issn}filename:\""+request.getParameter("contract")+"\"") 
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields(fl)
                    .setLimit(rows);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
                
            resp = solr.request(jrequest, "titles");
            is = (InputStream) resp.get("stream");
            ret.put("issn", (new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getJSONArray("docs"))); 
            

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject searchAuthors(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset"));
            }
            //String q = "nameIdentifier:*";
            String q = "*";
            if (request.getParameter("q") != null) {
                q = request.getParameter("q");
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("df", "full_text") 
                    .withFacet("labels", new TermsFacetMap("labels")
                            .setLimit(1000)
                            .setMinCount(1))
                    .withFacet("state", new TermsFacetMap("state")
                            .setLimit(1000)
                            .setMinCount(1)) 
                    .setOffset(offset)
                    .setSort("indextime desc")
                    .returnFields("*")
                    .setLimit(rows);


            if (request.getParameter("state") != null) {
                jrequest = jrequest.withFilter("state:" + request.getParameter("state"));
            }

            if (request.getParameter("labels") != null) {
                jrequest = jrequest.withFilter("labels:" + request.getParameter("labels"));
            }

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "authors");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8")); 

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject checkAuthor(HttpServletRequest request) {

        // https://nklink.toolforge.org/link.php?autid=kup19980000039661&format=json
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 10;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(request.getParameter("author"))
                    .withParam("df", "full_text")
                    .setLimit(rows);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "aut");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject getAuthorById(String id) {

        // https://nklink.toolforge.org/link.php?autid=kup19980000039661&format=json
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("controlfield_001:"+id)
                    .setLimit(1);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "aut");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject getDocByPid(String pid) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("pid:\""+pid + "\"")
                    .returnFields("*,authors_kramerius:[json],state_history:[json],release_date:sum(decision_date, if(or(eq (model,'monograph'),eq (model,'monographunit')),71,101))")
                    .setLimit(1);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject getRelated(String pid) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("root.pid:\""+pid + "\"")
                    .returnFields("*,authors_kramerius:[json],state_history:[json]")
                    .setLimit(200); 

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject getBatchDocs(String batch, boolean onlyPids) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("batch:\""+batch + "\"")
                    .withFacet("state", new TermsFacetMap("state")
                            .setLimit(1000)
                            .setMinCount(1)) 
                    .returnFields(onlyPids ? "pid" : "*,authors_kramerius:[json],state_history:[json]")
                    .setLimit(200); 

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject getLabelDocs(String label) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("labels:\""+label + "\"")
                    .returnFields("pid")
                    .setLimit(1000); 

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject getLabels() {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("id:labels")
                    .returnFields("value:[json]")
                    .setLimit(1);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "configuration");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getJSONArray("docs").getJSONObject(0);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject getConfig() {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .returnFields("id,value:[json]")
                    .setLimit(10);

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

    public static JSONObject getLinks(String autid) {

            JSONObject ret = new JSONObject();
        try {
            String url = "https://nklink.toolforge.org/link.php?format=json&autid=" + autid;
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .GET()
                    .uri(new URI(url))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            ret = new JSONObject(response.body());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject searchContracts(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.parseInt(rowsP);
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.parseInt(request.getParameter("offset"));
            }
            
            
            String sort = "indextime desc";
            if (request.getParameter("sort") != null) {
                sort = request.getParameter("sort");
            }
            
            String q = "*";
            if (request.getParameter("q") != null) {
                q = request.getParameter("q");
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("df", "search_text") 
                    .withFacet("hlavni", new TermsFacetMap("hlavni")
                            .setLimit(1000)
                            .setMinCount(1))
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields("*")
                    .setLimit(rows);

            if (request.getParameter("dodatek") != null) {
                jrequest = jrequest.withFilter("hlavni:false");
                jrequest = jrequest.withFilter("filename_search:" + request.getParameter("dodatek") + "*");
            }
            if (request.getParameter("hlavni_smlouva") != null) {
                jrequest = jrequest.withFilter("hlavni:true");
                jrequest = jrequest.withFilter("filename_search:" + request.getParameter("hlavni_smlouva") + "*");
            }
            
            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "contracts");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8")); 

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static JSONObject getContract(String filename){

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("filename:\""+filename + "\"")
                    .returnFields("*")
                    .setLimit(1);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "contracts");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
}
