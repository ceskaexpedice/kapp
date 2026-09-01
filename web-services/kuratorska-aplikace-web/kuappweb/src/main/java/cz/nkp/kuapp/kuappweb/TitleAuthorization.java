package cz.nkp.kuapp.kuappweb;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

public final class TitleAuthorization {

    private static final Logger LOGGER = Logger.getLogger(TitleAuthorization.class.getName());
    private static final String ADMIN_ROLE = "kappAdmin";
    private static final String MAPPING_TYPE = "library_physical_locations";

    private TitleAuthorization() {
    }

    public static boolean hasLibraryRole(HttpServletRequest request) {
        if (UserServlet.getUser(request) == null) {
            return false;
        }
        if (UserServlet.hasRole(request, ADMIN_ROLE)) {
            return true;
        }

        Set<String> roles = UserServlet.getRoles(request);
        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            SolrQuery mappingQuery = new SolrQuery("config_type:\"" + MAPPING_TYPE + "\"")
                    .setFields("library")
                    .setRows(1000);
            for (SolrDocument mapping : solr.query("configuration", mappingQuery).getResults()) {
                Object library = mapping.getFieldValue("library");
                if (library != null && roles.contains(library.toString())) {
                    return true;
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Unable to authorize library role", ex);
        }
        return false;
    }

    public static boolean canEditTitles(HttpServletRequest request, Collection<String> requestedPids) {
        if (UserServlet.getUser(request) == null) {
            return false;
        }
        if (UserServlet.hasRole(request, ADMIN_ROLE)) {
            return true;
        }

        Set<String> pids = requestedPids.stream()
                .filter(pid -> pid != null && !pid.isBlank())
                .collect(Collectors.toSet());
        if (pids.isEmpty()) {
            return false;
        }

        try (SolrClient solr = new HttpJdkSolrClient.Builder(Options.getInstance().getString("solr")).build()) {
            Set<String> allowedLocations = getAllowedPhysicalLocations(solr, UserServlet.getRoles(request));
            if (allowedLocations.isEmpty()) {
                return false;
            }

            SolrQuery titleQuery = new SolrQuery("*:*")
                    .addFilterQuery(anyOf("pid", pids))
                    .addFilterQuery(anyOf("physical_locations", allowedLocations))
                    .setRows(0);
            long matchingTitles = solr.query("titles", titleQuery).getResults().getNumFound();
            return matchingTitles == pids.size();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Unable to authorize title edit", ex);
            return false;
        }
    }

    private static Set<String> getAllowedPhysicalLocations(SolrClient solr, Set<String> roles) throws Exception {
        Set<String> locations = new HashSet<>();
        if (roles.isEmpty()) {
            return locations;
        }

        SolrQuery mappingQuery = new SolrQuery("config_type:\"" + MAPPING_TYPE + "\"")
                .setFields("library", "physical_locations")
                .setRows(1000);
        QueryResponse response = solr.query("configuration", mappingQuery);
        SolrDocumentList mappings = response.getResults();
        for (SolrDocument mapping : mappings) {
            Object library = mapping.getFieldValue("library");
            if (library == null || !roles.contains(library.toString())) {
                continue;
            }
            Collection<Object> mappedLocations = mapping.getFieldValues("physical_locations");
            if (mappedLocations != null) {
                mappedLocations.stream()
                        .filter(value -> value != null)
                        .map(Object::toString)
                        .forEach(locations::add);
            }
        }
        return locations;
    }

    private static String anyOf(String field, Collection<String> values) {
        String terms = values.stream()
                .map(ClientUtils::escapeQueryChars)
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(" OR "));
        return field + ":(" + terms + ")";
    }
}
