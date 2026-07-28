package cz.nkp.kuapp.kuappweb;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
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
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Public read-only endpoint returning titles whose state changed in a given
 * Unix timestamp interval.
 */
@WebServlet(name = "StateChangesServlet", urlPatterns = {"/state-changes"})
public class StateChangesServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StateChangesServlet.class.getName());
    private static final int PAGE_SIZE = 1000;
    private static final String FIRST_CURSOR = "*";
    private static final String RETURN_FIELDS = "pid,title,assigned_licenses,licenses,state_date,state_user";

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

        final Instant start;
        final Instant end;
        try {
            start = parseRequiredUnixTimestamp(request.getParameter("startDate"), "startDate");
            end = parseOptionalUnixTimestamp(request.getParameter("endDate"), "endDate", clock.instant());
            if (end.isBefore(start)) {
                throw new IllegalArgumentException("endDate must be greater than or equal to startDate");
            }
        } catch (IllegalArgumentException ex) {
            writeError(response, HttpServletResponse.SC_BAD_REQUEST, ex.getMessage());
            return;
        }

        try (SolrClient solr = new HttpJdkSolrClient.Builder(
                Options.getInstance().getString("solr")).build()) {
            response.getWriter().println(findStateChanges(solr, start, end).toString(2));
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Unable to retrieve state changes", ex);
            writeError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to retrieve state changes");
        }
    }

    private static JSONArray findStateChanges(SolrClient solr, Instant start, Instant end)
            throws Exception {
        JSONArray result = new JSONArray();
        String cursor = FIRST_CURSOR;

        while (true) {
            JsonQueryRequest query = new JsonQueryRequest()
                    .setQuery("*:*")
                    .withFilter("state_date:[" + start + " TO " + end + "]")
                    .withParam("cursorMark", cursor)
                    .setSort("pid asc")
                    .setLimit(PAGE_SIZE)
                    .returnFields(RETURN_FIELDS);
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
        return result;
    }

    private static void copyIfPresent(JSONObject source, JSONObject target,
            String sourceName, String targetName) {
        if (source.has(sourceName)) {
            target.put(targetName, source.get(sourceName));
        } else {
            target.put(targetName, JSONObject.NULL);
        }
    }

    static Instant parseRequiredUnixTimestamp(String value, String parameterName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(parameterName + " is required");
        }
        return parseUnixTimestamp(value, parameterName);
    }

    static Instant parseOptionalUnixTimestamp(String value, String parameterName,
            Instant defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return parseUnixTimestamp(value, parameterName);
    }

    private static Instant parseUnixTimestamp(String value, String parameterName) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException | DateTimeException ex) {
            throw new IllegalArgumentException(
                    parameterName + " must be a valid Unix timestamp in seconds");
        }
    }

    private static void prepareResponse(HttpServletResponse response) {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);
    }

    private static void writeError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.getWriter().println(new JSONObject().put("error", message).toString());
    }
}
