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

    public static JSONObject search(HttpServletRequest request) {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 0;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.valueOf(rowsP);
            }
            
            int offset = 0;
            if (request.getParameter("offset") != null) {
                offset = Integer.valueOf(request.getParameter("offset"));
            }
            String q = "*";
            if (request.getParameter("q") != null) {
                q = request.getParameter("q");
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("df", "root.title")
                    .withFacet("authors", new TermsFacetMap("authors")
                            .setLimit(1000)
                            .setMinCount(1))
                    .withFacet("public", new TermsFacetMap("public")
                            .setLimit(1000)
                            .setMinCount(1))
                    .withFacet("physical_locations", new TermsFacetMap("physical_locations.facet")
                            .setLimit(1000)
                            .setMinCount(1))
                    .setOffset(offset)
                    .setLimit(rows);

            if (request.getParameter("author") != null) {
                jrequest = jrequest.withFilter("{!tag=ffauthors}authors:(\"" + String.join("\" OR \"", request.getParameterValues("author")) + "\")");
            }

            if (request.getParameter("public") != null) {
                jrequest = jrequest.withFilter("public:" + request.getParameter("public"));
            }

            if (request.getParameter("physical_locations") != null) {
                jrequest = jrequest.withFilter("{!tag=fffl}physical_locations.facet:(\"" + String.join("\" OR \"", request.getParameterValues("physical_locations")) + "\")");
            }

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "testing");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject checkAutor(HttpServletRequest request) {

        // https://nklink.toolforge.org/link.php?autid=kup19980000039661&format=json
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            String rowsP = request.getParameter("rows");
            int rows = 10;
            if (rowsP != null && !rowsP.isBlank()) {
                rows = Integer.valueOf(rowsP);
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(request.getParameter("author"))
                    .withParam("defType", "dismax")
                    .withParam("qf", "name")
                    .withParam("mm", "80%")
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
