package cz.nkp.kuapp.kuappweb.index;

import cz.nkp.kuapp.kuappweb.Options;
import cz.nkp.kuapp.kuappweb.UserServlet;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.DomainMap;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.request.json.QueryFacetMap;
import org.apache.solr.client.solrj.request.json.TermsFacetMap;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class IndexSearcher {

    public static final Logger LOGGER = Logger.getLogger(IndexSearcher.class.getName());
    // final static SimpleDateFormat dtformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

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

            resp = solr.request(jrequest, "contracts");
            is = (InputStream) resp.get("stream");
            ret.put("contracts", (new JSONObject(IOUtils.toString(is, "UTF-8")).getJSONObject("response").getInt("numFound")));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }
    
    public static Object[] intersection(String[] a, String[] b) {
      if (a == null || b == null) {
        return new Object[]{};
      }
      return Arrays.stream(a)
                     .distinct()
                     .filter(x -> Arrays.stream(b).anyMatch(y -> y.equals(x)))
                     .toArray();
    }

    private static JsonQueryRequest addNegatedFilters(HttpServletRequest request,
            JsonQueryRequest query, String parameter, String field) {
        if (request.getParameter(parameter) == null) {
            return query;
        }
        for (String value : request.getParameterValues(parameter)) {
            query = query.withFilter("-" + field + ":\"" + ClientUtils.escapeQueryChars(value) + "\"");
        }
        return query;
    }
            
    //final static String release_date = "if(exists(last_year_death),sum(last_year_death, 71),sum(date_issued_year, if(or(eq (model,'monograph'),eq (model,'monographunit')),71,101)))";
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
            
            String fl = "*,authors_kramerius:[json],authors_kapp:[json],state_history:[json],release_year";
            if (request.getParameter("fl") != null) {
                fl = String.join(",",request.getParameterValues("fl"));
                fl = fl.replace("action", "");
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
                    .withParam("fq", "{!collapse field=root_pid nullPolicy=expand}")
                    .withParam("expand", true)
                    .withParam("expand.rows", 100)
                    .withParam("modelboost", "model:monograph")
                    
                    .withFacet("state_date", new QueryFacetMap("state_date:[1000-01-01T00:00:00Z TO 2100-12-01T00:00:00Z]")
                            .withStatSubFacet("state_date", "min(state_date)")
                            .withStatSubFacet("state_date", "max(state_date)"))
                    
                    .withFacet("last_modified_date", new QueryFacetMap("last_modified_date:[1000-01-01T00:00:00Z TO 2100-12-01T00:00:00Z]")
                            .withStatSubFacet("last_modified_date", "min(last_modified_date)")
                            .withStatSubFacet("last_modified_date", "max(last_modified_date)"))
                    
                    .withFacet("date_issued_year", new QueryFacetMap("date_issued_year:[1000 TO 2100]")
                            .withStatSubFacet("date_issued_year_min", "min(date_issued_year)")
                            .withStatSubFacet("date_issued_year_max", "max(date_issued_year)"))
                    .withFacet("modified_by_user", new TermsFacetMap("modified_by_user")
                            .setLimit(100)
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
                    .withFacet("assigned_licenses", new TermsFacetMap("assigned_licenses")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("licenses", new TermsFacetMap("licenses")
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("digital_library", new TermsFacetMap("digital_library")
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
                    .withFacet("physical_locations", new TermsFacetMap("physical_locations")
                            .withDomain(new DomainMap().withTagsToExclude("fffl"))
                            .setLimit(100)
                            .setMinCount(1))
                    .withFacet("contracts", new TermsFacetMap("contracts")
                            .setLimit(100)
                            .setMinCount(1))
//                    .withFacet("date_issued_year", new TermsFacetMap("date_issued_year")
//                            .setSort("index asc")
//                            .setLimit(20)
//                            .setMinCount(1))
//                    .withParam("stats", true)
//                    .withParam("stats.field", "date_issued_year")
                    .setOffset(offset)
                    .returnFields(fl)
                    .setSort(sort)
                    .setLimit(rows);

            if (UserServlet.getUser(request) != null) {
                jrequest = jrequest.withFacet("batch", new TermsFacetMap("batch")
                        .setLimit(100)
                        .setMinCount(1));
            }

            if (request.getParameter("model") != null) {
                jrequest = jrequest.withFilter("model:" + request.getParameter("model"));
            } else {
                if (request.getParameter("q") == null) {
                  String[] labels = request.getParameterValues("labels");
                  String[] error_labels = Options.getInstance().getStrings("error_labels");
                  if (intersection(labels, error_labels).length == 0) {
                    jrequest = jrequest.withFilter("model:(monograph OR periodical)");
                  }
                    
                }
            }

            if (request.getParameter("author") != null) {
                jrequest = jrequest.withFilter("{!tag=ffauthors}authors:(\"" + String.join("\" OR \"", request.getParameterValues("author")) + "\")");
            }

            if (request.getParameter("batch") != null) {
                jrequest = jrequest.withFilter("batch:\"" + request.getParameter("batch") + "\"");
            }

            if (request.getParameter("state") != null) {
                jrequest = jrequest.withFilter("state:" + request.getParameter("state"));
            }

            if (request.getParameter("modified_by_user") != null) {
                jrequest = jrequest.withFilter("modified_by_user:\"" + request.getParameter("modified_by_user") + "\"");
            }

            if (request.getParameter("labels") != null) {
                for (String label : request.getParameterValues("labels")) {
                    jrequest = jrequest.withFilter("labels:\"" + label + "\"");
                }
            }

            if (request.getParameter("contracts") != null) {
                jrequest = jrequest.withFilter("contracts:\"" + request.getParameter("contracts") + "\"");
            }

            if (request.getParameter("licenses") != null) {
                jrequest = jrequest.withFilter("licenses:" + request.getParameter("licenses"));
            }

            if (request.getParameter("digital_library") != null) {
                // jrequest = jrequest.withFilter("digital_library:" + request.getParameter("digital_library"));
                jrequest = jrequest.withFilter("digital_library:(\"" + String.join("\" AND \"", request.getParameterValues("digital_library")) + "\")");
            }
            if (request.getParameter("assigned_licenses") != null) {
                jrequest = jrequest.withFilter("assigned_licenses:" + request.getParameter("assigned_licenses"));
            }

            if (request.getParameter("physical_locations") != null) {
                jrequest = jrequest.withFilter("{!tag=fffl}physical_locations:(\"" + String.join("\" OR \"", request.getParameterValues("physical_locations")) + "\")");
            }

            if (request.getParameter("date_issued_year") != null) {
                String[] parts = request.getParameter("date_issued_year").split(",");
                String from = parts[0].isBlank() ? "*" : parts[0];
                String to = parts[1].isBlank() ? "*" : parts[1];
                jrequest = jrequest.withFilter("date_issued_year:[" + from + " TO " + to + "]");
            }

            if (request.getParameter("state_date") != null) {
                String[] parts = request.getParameter("state_date").split(",");
                String from = parts[0].isBlank() ? "*" : parts[0];
                String to = parts[1].isBlank() ? "*" : parts[1];
                jrequest = jrequest.withFilter("state_date:[" + from + "T00:00:00Z TO " + to + "T23:59:59Z]");
            }

            if (request.getParameter("last_modified_date") != null) {
                String[] parts = request.getParameter("last_modified_date").split(",");
                String from = parts[0].isBlank() ? "*" : parts[0];
                String to = parts[1].isBlank() ? "*" : parts[1];
                jrequest = jrequest.withFilter("last_modified_date:[" + from + "T00:00:00Z TO " + to + "T23:59:59Z]");
            }
            if (request.getParameter("authors_ids") != null) {
                jrequest = jrequest.withFilter("authors_ids:" + request.getParameter("authors_ids"));
            }

            jrequest = addNegatedFilters(request, jrequest, "model_not", "model");
            jrequest = addNegatedFilters(request, jrequest, "author_not", "authors");
            jrequest = addNegatedFilters(request, jrequest, "batch_not", "batch");
            jrequest = addNegatedFilters(request, jrequest, "state_not", "state");
            jrequest = addNegatedFilters(request, jrequest, "modified_by_user_not", "modified_by_user");
            jrequest = addNegatedFilters(request, jrequest, "labels_not", "labels");
            jrequest = addNegatedFilters(request, jrequest, "contracts_not", "contracts");
            jrequest = addNegatedFilters(request, jrequest, "licenses_not", "licenses");
            jrequest = addNegatedFilters(request, jrequest, "digital_library_not", "digital_library");
            jrequest = addNegatedFilters(request, jrequest, "assigned_licenses_not", "assigned_licenses");
            jrequest = addNegatedFilters(request, jrequest, "physical_locations_not", "physical_locations");
            jrequest = addNegatedFilters(request, jrequest, "authors_ids_not", "authors_ids");

            if (request.getParameter("export") != null) {
                jrequest.setResponseParser(new InputStreamResponseParser("csv"));
            } else {
                jrequest.setResponseParser(new InputStreamResponseParser("json"));
            }
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");

            if (request.getParameter("export") != null) {
                ret.put("csv", IOUtils.toString(is, "UTF-8"));
            } else {
                ret = new JSONObject(IOUtils.toString(is, "UTF-8"));
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject searchTitlesByAuthor(HttpServletRequest request) {
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
            String fl = Boolean.parseBoolean(request.getParameter("only_pid"))
                    ? "pid"
                    : "pid,authors_kramerius:[json],authors_kapp:[json]";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withFilter("authors_ids:(\"" + request.getParameter("author_id") + "\")")
                    .withParam("fq", "{!collapse field=root_pid}")
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields(fl)
                    .setLimit(rows);

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

    public static JSONObject searchTitlesByAuthors(HttpServletRequest request) {
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
            String fl = "pid";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withFilter("authors_ids:(\"" + request.getParameter("author_id") + "\")")
                    .withParam("fq", "{!collapse field=root_pid}")
                    .setOffset(offset)
                    .returnFields(fl)
                    .setLimit(rows);

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
                    .withParam("fq", "{!collapse field=root_pid}")
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields("*,authors_kramerius:[json],authors_kapp:[json],state_history:[json],release_year")
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
            String fl = "*,authors_kramerius:[json],authors_kapp:[json],state_history:[json],release_year";
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("fq", "{!join from=ccnb fromIndex=contracts to=id_ccnb}filename:\"" + request.getParameter("contract") + "\"")
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
                    .withParam("fq", "{!join from=issn fromIndex=contracts to=id_issn}filename:\"" + request.getParameter("contract") + "\"")
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
            String sort = "indextime desc";
            if (request.getParameter("q") != null) {
                q = request.getParameter("q");
                sort = "score desc";
            }
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery(q)
                    .withParam("df", "full_text")
                    .withFacet("labels", new TermsFacetMap("labels")
                            .setLimit(1000)
                            .setMinCount(1))
                    .withFacet("no_nameIdentifier", new QueryFacetMap("-nameIdentifier:*"))
                    .withFacet("no_year_death", new QueryFacetMap("-year_death:*"))
                    .setOffset(offset)
                    .setSort(sort)
                    .returnFields("*,date_birth:date_issued_start_year,date_death:date_issued_end_year ")
                    .setLimit(rows);

            if (request.getParameter("state") != null) {
                jrequest = jrequest.withFilter("state:" + request.getParameter("state"));
            }

            if (request.getParameter("labels") != null) {
                for (String label : request.getParameterValues("labels")) {
                    jrequest = jrequest.withFilter("labels:\"" + label + "\"");
                }
            }

            if (request.getParameter("no_nameIdentifier") != null) {
                jrequest = jrequest.withFilter("-nameIdentifier:*");
            }

            if (request.getParameter("no_year_death") != null) {
                jrequest = jrequest.withFilter("-year_death:*");
            }

            jrequest = addNegatedFilters(request, jrequest, "state_not", "state");
            jrequest = addNegatedFilters(request, jrequest, "labels_not", "labels");
            if (request.getParameter("no_nameIdentifier_not") != null) {
                jrequest = jrequest.withFilter("nameIdentifier:*");
            }
            if (request.getParameter("no_year_death_not") != null) {
                jrequest = jrequest.withFilter("year_death:*");
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
                    //.withParam("fq", "{!join fromIndex=authors from=nameIdentifier to=controlfield_001}full_text:\""+request.getParameter("author")+"\"") 
                    .withParam("df", "full_text")
                    .setLimit(rows);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "aut");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"));
            // Find existing authors in aothors by nameIdentifier. Add field to response
            JSONArray docs = ret.getJSONObject("response").getJSONArray("docs");
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.getJSONObject(i);
                JSONObject a = getAuthorById(doc.getString("controlfield_001"));
                if (a.getJSONObject("response").getInt("numFound") > 0) {
                    doc.put("existing_author", a.getJSONObject("response").getJSONArray("docs").getJSONObject(0));
                }
            }

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject getAuthorById(String id) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .withFilter("id:" + id)
                    .returnFields("*")
                    .setLimit(1);

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

    public static JSONObject getAutById(String id) {

        // https://nklink.toolforge.org/link.php?autid=kup19980000039661&format=json
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("controlfield_001:" + id)
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

    public static JSONObject getTitleByPid(String pid) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("pid:\"" + pid + "\"")
                    .returnFields("*,authors_kramerius:[json],authors_kapp:[json],state_history:[json],release_year")
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
                    .setQuery("root_pid:\"" + pid + "\"")
                    .returnFields("*,authors_kramerius:[json],authors_kapp:[json],state_history:[json]")
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

    public static JSONObject getUsedBatches() {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .withFacet("batches", new TermsFacetMap("batch")
                            .setLimit(1000)
                            .setMinCount(1))
                    .setLimit(0);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"))
                    .getJSONObject("facets").getJSONObject("batches");

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
                    .setQuery("batch:\"" + batch + "\"")
                    .withFacet("state", new TermsFacetMap("state")
                            .setLimit(1000)
                            .setMinCount(1))
                    .returnFields(onlyPids ? "pid" : "*,authors_kramerius:[json],authors_kapp:[json],state_history:[json]")
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
                    .setQuery("labels:\"" + label + "\"")
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

    public static JSONObject getLabels(HttpServletRequest request) {
        JSONObject config = getConfig(request);
        JSONArray docs = config.optJSONArray("config");
        if (docs == null) {
            return config;
        }

        JSONArray labels = new JSONArray();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            if (!isLabelConfigId(doc.optString("id"))) {
                continue;
            }
            JSONArray scopedLabels = doc.optJSONArray("value");
            if (scopedLabels == null) {
                continue;
            }
            for (int j = 0; j < scopedLabels.length(); j++) {
                JSONObject label = scopedLabels.optJSONObject(j);
                if (label != null && names.add(label.optString("name"))) {
                    labels.put(label);
                }
            }
        }
        return new JSONObject().put("value", labels);
    }

    public static JSONObject getConfig(HttpServletRequest request) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .returnFields("id,value:[json],config_type,library,physical_locations,label_visibility,label_owner")
                    .setLimit(1000);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "configuration");
            InputStream is = (InputStream) resp.get("stream");
            JSONArray docs = new JSONObject(IOUtils.toString(is, "UTF-8"))
                    .getJSONObject("response").getJSONArray("docs");
            ret.put("config", filterLabelConfigs(request, docs));

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static Set<String> getDigitalLibraries() {
        Set<String> libraries = new HashSet<>();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest request = new JsonQueryRequest()
                    .setQuery("id:digitalni_knihovny")
                    .returnFields("value:[json]")
                    .setLimit(1);
            request.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> response = solr.request(request, "configuration");
            InputStream stream = (InputStream) response.get("stream");
            JSONArray docs = new JSONObject(IOUtils.toString(stream, "UTF-8"))
                    .getJSONObject("response").getJSONArray("docs");
            if (docs.length() > 0) {
                JSONArray values = docs.getJSONObject(0).optJSONArray("value");
                if (values != null) {
                    for (int i = 0; i < values.length(); i++) {
                        String library = values.optString(i, null);
                        if (library != null && !library.isBlank()) {
                            libraries.add(library);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return libraries;
    }

    public static Set<String> getVisibleLabelStorageNames(HttpServletRequest request) {
        Set<String> names = new HashSet<>();
        JSONArray docs = getConfig(request).optJSONArray("config");
        if (docs == null) {
            return names;
        }
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String id = doc.optString("id");
            if (!isLabelConfigId(id)) {
                continue;
            }
            String visibility = doc.optString("label_visibility", "labels".equals(id) ? "everyone" : "");
            String owner = doc.optString("label_owner", "");
            if (owner.isBlank() && !"labels".equals(id)) {
                owner = id.substring("labels_".length());
            }
            JSONArray labels = doc.optJSONArray("value");
            if (labels == null) {
                continue;
            }
            for (int j = 0; j < labels.length(); j++) {
                JSONObject label = labels.optJSONObject(j);
                if (label == null || label.optString("name").isBlank()) {
                    continue;
                }
                String name = label.getString("name");
                names.add("everyone".equals(visibility) || owner.isBlank() ? name : name + "_" + owner);
            }
        }
        return names;
    }

    private static JSONArray filterLabelConfigs(HttpServletRequest request, JSONArray docs) {
        if (UserServlet.hasRole(request, "kappAdmin")) {
            return docs;
        }

        Set<String> visibleIds = new HashSet<>();
        visibleIds.add("labels");
        String username = UserServlet.getUserName(request);
        if (username != null && !username.isBlank()) {
            visibleIds.add("labels_" + username);
        }
        Set<String> roles = UserServlet.getRoles(request);
        for (String library : getDigitalLibrariesFromDocs(docs)) {
            if (roles.contains(library)) {
                visibleIds.add("labels_" + library);
            }
        }

        JSONArray filtered = new JSONArray();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String id = doc.optString("id");
            if (!isLabelConfigId(id) || visibleIds.contains(id)) {
                filtered.put(doc);
            }
        }
        return filtered;
    }

    private static Set<String> getDigitalLibrariesFromDocs(JSONArray docs) {
        Set<String> libraries = new HashSet<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            if (!"digitalni_knihovny".equals(doc.optString("id"))) {
                continue;
            }
            JSONArray values = doc.optJSONArray("value");
            if (values != null) {
                for (int j = 0; j < values.length(); j++) {
                    String library = values.optString(j, null);
                    if (library != null && !library.isBlank()) {
                        libraries.add(library);
                    }
                }
            }
        }
        return libraries;
    }

    private static boolean isLabelConfigId(String id) {
        return "labels".equals(id) || id.startsWith("labels_");
    }

    public static JSONObject getPhysicalLocations() {
        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("*")
                    .withFacet("physical_locations", new TermsFacetMap("physical_locations")
                            .setLimit(-1)
                            .setMinCount(1))
                    .setLimit(0);

            jrequest.setResponseParser(new InputStreamResponseParser("json"));
            NamedList<Object> resp = solr.request(jrequest, "titles");
            InputStream is = (InputStream) resp.get("stream");
            ret = new JSONObject(IOUtils.toString(is, "UTF-8"))
                    .getJSONObject("facets")
                    .getJSONObject("physical_locations");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex.toString());
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
            LOGGER.log(Level.SEVERE, "Error getting links for {0}: {1}", new Object[]{autid, ex});
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
            String content = IOUtils.toString(is, "UTF-8");
            ret = new JSONObject(content);

        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            ret.put("error", ex);
        }
        return ret;
    }

    public static JSONObject getContract(String filename) {

        JSONObject ret = new JSONObject();
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            JsonQueryRequest jrequest = new JsonQueryRequest()
                    .setQuery("filename:\"" + filename + "\"")
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
