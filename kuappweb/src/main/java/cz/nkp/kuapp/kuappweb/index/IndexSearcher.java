package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.apache.solr.common.util.NamedList;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class IndexSearcher {

    public static final Logger LOGGER = Logger.getLogger(IndexSearcher.class.getName());
    final static SimpleDateFormat dtformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    public static JSONObject searchTitles(HttpServletRequest request) {
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
            String q = "*";
            if (request.getParameter("q") != null) {
                q = "{!boost b=query($modelboost)}" + request.getParameter("q");
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
                    .withFacet("licenses", new TermsFacetMap("licenses")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("state", new TermsFacetMap("state")
                            .setLimit(20)
                            .setMinCount(1)) 
                    .withFacet("physical_locations", new TermsFacetMap("physical_locations.facet")
                            .setLimit(100)
                            .setMinCount(1))
                    .setOffset(offset)
                    .setSort("indextime desc")
                    .returnFields("*,authors_kramerius:[json],state_history:[json]")
                    .setLimit(rows);

            if (request.getParameter("author") != null) {
                jrequest = jrequest.withFilter("{!tag=ffauthors}authors:(\"" + String.join("\" OR \"", request.getParameterValues("author")) + "\")");
            }

            if (request.getParameter("state") != null) {
                jrequest = jrequest.withFilter("state:" + request.getParameter("state"));
            }

            if (request.getParameter("labels") != null) {
                jrequest = jrequest.withFilter("labels:" + request.getParameter("labels"));
            }

            if (request.getParameter("licenses") != null) {
                jrequest = jrequest.withFilter("licenses:" + request.getParameter("licenses"));
            }

            if (request.getParameter("physical_locations") != null) {
                jrequest = jrequest.withFilter("{!tag=fffl}physical_locations.facet:(\"" + String.join("\" OR \"", request.getParameterValues("physical_locations")) + "\")");
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
                    .returnFields("*,authors_kramerius:[json],state_history:[json]")
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
}
