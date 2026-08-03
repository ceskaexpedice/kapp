package cz.nkp.kuapp.kuappweb;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Public read-only endpoint returning documents whose assigned licence
 * changed in a given interval.
 */
@WebServlet(name = "StateChangesServlet", urlPatterns = {"/changes"})
public class StateChangesServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StateChangesServlet.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String FIRST_CURSOR = "*";
    private static final String RETURN_FIELDS
            = "pid,title,assigned_licenses,licenses,digital_library,state_date,state_user";

    private final Clock clock;

    public StateChangesServlet() {
        this(Clock.systemUTC());
    }

    StateChangesServlet(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        prepareResponse(response);

        final Instant from;
        final Instant to;
        final String license;
        final String source;
        try {
            from = parseOptionalDateTime(request.getParameter("from"), "from", null);
            to = parseOptionalDateTime(request.getParameter("to"), "to", clock.instant());
            if (from != null && to.isBefore(from)) {
                throw new IllegalArgumentException(
                        "Parameter 'to' must be greater than or equal to parameter 'from'.");
            }
            license = parseLicense(request.getParameter("license"));
            source = parseSource(request.getParameter("source"));
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "invalid_request", ex.getMessage());
            return;
        }

        try (SolrClient solr = new HttpJdkSolrClient.Builder(
                Options.getInstance().getString("solr")).build()) {
            response.getWriter().println(
                    findLicenseChanges(solr, from, to, license, source).toString(2));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Unable to retrieve licence changes", ex);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "internal_error", "Unable to retrieve licence changes.");
        }
    }

    private static JSONArray findLicenseChanges(SolrClient solr, Instant from,
            Instant to, String license, String source) throws Exception {
        JSONArray result = new JSONArray();
        String cursor = FIRST_CURSOR;
        String fromBoundary = from == null ? "*" : from.toString();

        while (true) {
            JsonQueryRequest query = new JsonQueryRequest()
                    .setQuery("*:*")
                    .withFilter("state_date:[" + fromBoundary + " TO " + to + "]")
                    .withFilter("assigned_licenses:[* TO *]")
                    .withParam("cursorMark", cursor)
                    .setSort("pid asc")
                    .setLimit(PAGE_SIZE)
                    .returnFields(RETURN_FIELDS);
            if (license != null) {
                query = query.withFilter("assigned_licenses:" + license);
            }
            if (source != null) {
                query = query.withFilter("digital_library:\""
                        + ClientUtils.escapeQueryChars(source) + "\"");
            }
            query.setResponseParser(new InputStreamResponseParser("json"));

            NamedList<Object> solrResponse = solr.request(query, "titles");
            try (InputStream stream = (InputStream) solrResponse.get("stream")) {
                JSONObject page = new JSONObject(IOUtils.toString(stream, "UTF-8"));
                JSONArray documents = page.getJSONObject("response").getJSONArray("docs");
                for (int i = 0; i < documents.length(); i++) {
                    result.put(toResponseDocument(documents.getJSONObject(i)));
                }

                String nextCursor = page.getString("nextCursorMark");
                if (nextCursor.equals(cursor)) {
                    break;
                }
                cursor = nextCursor;
            }
        }

        return result;
    }

    static JSONObject toResponseDocument(JSONObject source) {
        JSONObject result = new JSONObject();
        copyIfPresent(source, result, "pid", "pid");
        copyIfPresent(source, result, "title", "title");
        copyIfPresent(source, result, "assigned_licenses", "assigned_licenses");
        copyIfPresent(source, result, "licenses", "licenses");
        copyIfPresent(source, result, "state_date", "stateDate");
        copyIfPresent(source, result, "state_user", "stateUser");
        copyDigitalLibrary(source, result);
        return result;
    }

    private static void copyDigitalLibrary(JSONObject source, JSONObject target) {
        Object digitalLibrary = source.opt("digital_library");
        if (digitalLibrary instanceof JSONArray values) {
            target.put("source", values.isEmpty() ? JSONObject.NULL : values.get(0));
        } else {
            target.put("source", digitalLibrary == null ? JSONObject.NULL : digitalLibrary);
        }
    }

    private static void copyIfPresent(JSONObject source, JSONObject target,
            String sourceName, String targetName) {
        if (source.has(sourceName)) {
            target.put(targetName, source.get(sourceName));
        } else {
            target.put(targetName, JSONObject.NULL);
        }
    }

    static Instant parseOptionalDateTime(String value, String parameterName,
            Instant defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(
                    "Parameter '" + parameterName
                    + "' must be a valid UTC ISO 8601 date-time.");
        }
    }

    private static String parseLicense(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.equals("public") && !value.equals("onsite")) {
            throw new IllegalArgumentException(
                    "Parameter 'license' must be either 'public' or 'onsite'.");
        }
        return value;
    }

    private static String parseSource(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("Parameter 'source' must not be blank.");
        }
        return value;
    }

    private static void prepareResponse(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private static void writeError(HttpServletResponse response, int status,
            String code, String message) throws IOException {
        response.setStatus(status);
        response.getWriter().println(new JSONObject()
                .put("code", code)
                .put("message", message)
                .toString());
    }
}
