package cz.nkp.kuapp.kuappweb.rest;

import cz.nkp.kuapp.kuappweb.Options;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.impl.InputStreamResponseParser;
import org.apache.solr.client.solrj.request.json.JsonQueryRequest;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.json.JSONObject;

@Path("v1/changes")
public class ChangesResource {

    private static final Logger LOGGER = Logger.getLogger(ChangesResource.class.getName());
    private static final Set<String> SUPPORTED_LIBRARIES = Set.of("mzk", "nkp");
    private static final Pattern SOLR_DATE_PATTERN = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2}(?:T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{3})?Z)?$");
    private static final String TITLES_CORE = "titles";
    private static final String INITIAL_CURSOR = "*";
    private static final String DEFAULT_SORT = "state_date desc,pid asc";
    private static final String DEFAULT_FIELDS = String.join(",",
            "pid",
            "root_pid",
            "title",
            "model",
            "digital_library",
            "state",
            "assigned_licenses",
            "contracts",
            "date_issued_year"
            );

    @GET
    @Path("{changeType}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response changes(
            @PathParam("changeType") String changeType,
            @QueryParam("library") String library,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("assignedLicense") List<String> assignedLicenses,
            @QueryParam("rows") String rowsParam,
            @QueryParam("cursor") String cursorParam) {

        LOGGER.log(Level.INFO, String.format( "changeType %s, library %s, from %s, to %s, assignedLicense %s, rows %s", changeType, library, from, to, assignedLicenses, rowsParam));

        String normalizedLibrary = normalizeLibrary(library);
        ChangeType normalizedChangeType = normalizeChangeType(changeType);
        validateDate("from", from);
        validateDate("to", to);

        int rows = parseBoundedInt("rows", rowsParam, 100, 1, 1000);
        String cursor = normalizeCursor(cursorParam);

        String solrAddr = Options.getInstance().getString("solr");
        try (SolrClient solr = new HttpJdkSolrClient.Builder(solrAddr).build()) {
            if (normalizedChangeType == ChangeType.SETS) {
                return Response.ok(loadSetChanges(solr, normalizedLibrary, from, to,
                        assignedLicenses, rows, cursor).toString(2),
                        MediaType.APPLICATION_JSON).build();
            }

            JsonQueryRequest request = new JsonQueryRequest()
                    .setQuery("*")
                    .withFilter("digital_library:\"" + ClientUtils.escapeQueryChars(normalizedLibrary) + "\"")
                    .withFilter("state_date:*")
                    .withFilter("NOT state:TO_CONTROL")
                    .withFilter("NOT state:FOR_REVIEW")
                    .setLimit(rows)
                    .setSort(DEFAULT_SORT)
                    .withParam("cursorMark", cursor)
                    .returnFields(DEFAULT_FIELDS);

            LOGGER.info("Request: " + request.toString() + "");

            request = addDateRangeFilter(request, from, to);
            request = addSimpleMonographsFilter(request);
            request = addMultiValueFilter(request, "assigned_licenses", assignedLicenses);
            request.setResponseParser(new InputStreamResponseParser("json"));

            NamedList<Object> response = solr.request(request, TITLES_CORE);
            InputStream stream = (InputStream) response.get("stream");
            JSONObject solrJson = new JSONObject(IOUtils.toString(stream, "UTF-8"));

            JSONObject result = new JSONObject()
                    .put("library", normalizedLibrary)
                    .put("type", normalizedChangeType.pathValue)
                    .put("rows", rows)
                    .put("cursor", cursor)
                    .put("nextCursor", solrJson.optString("nextCursorMark", cursor))
                    .put("response", solrJson.getJSONObject("response"));

            LOGGER.log(Level.INFO, "Changes: {0}", result.toString(2));

            return Response.ok(result.toString(2), MediaType.APPLICATION_JSON).build();
        } catch (WebApplicationException ex) {
            throw ex;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Error loading changes", ex);
            throw new WebApplicationException(error("SOLR_ERROR", ex.getMessage()), Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private static JSONObject loadSetChanges(SolrClient solr, String library, String from, String to,
            List<String> assignedLicenses, int rows, String cursor) throws Exception {
        JsonQueryRequest parentRequest = new JsonQueryRequest()
                .setQuery("*")
                .withFilter("digital_library:\"" + ClientUtils.escapeQueryChars(library) + "\"")
                .withFilter("model:(monograph OR periodical)")
                .withFilter("state:(PARENT_CLOSED)")
                .withFilter("state_date:*")
                .setLimit(rows)
                .setSort(DEFAULT_SORT)
                .withParam("cursorMark", cursor)
                .returnFields(DEFAULT_FIELDS);

        parentRequest = addDateRangeFilter(parentRequest, from, to);
        parentRequest.setResponseParser(new InputStreamResponseParser("json"));

        JSONObject solrJson = executeJson(solr, parentRequest);
        JSONObject response = solrJson.getJSONObject("response");
        JSONArray parents = response.getJSONArray("docs");
        JSONArray enrichedParents = new JSONArray();

        for (int i = 0; i < parents.length(); i++) {
            JSONObject parent = parents.getJSONObject(i);
            JSONArray children = loadChildren(solr, parent.getString("pid"), library, assignedLicenses);
            parent.put("children", children);
            enrichedParents.put(parent);
        }

        response.put("docs", enrichedParents);
        return new JSONObject()
                .put("library", library)
                .put("type", "sets")
                .put("rows", rows)
                .put("cursor", cursor)
                .put("nextCursor", solrJson.optString("nextCursorMark", cursor))
                .put("response", response);
    }

    private static JSONArray loadChildren(SolrClient solr, String rootPid, String library,
            List<String> assignedLicenses) throws Exception {
        JsonQueryRequest childRequest = new JsonQueryRequest()
                .setQuery("*")
                .withFilter("root_pid:\"" + ClientUtils.escapeQueryChars(rootPid) + "\"")
                .withFilter("-pid:\"" + ClientUtils.escapeQueryChars(rootPid) + "\"")
                .withFilter("digital_library:\"" + ClientUtils.escapeQueryChars(library) + "\"")
                .setLimit(1000)
                .setSort("pid asc")
                .returnFields(DEFAULT_FIELDS);

        childRequest = addMultiValueFilter(childRequest, "assigned_licenses", assignedLicenses);
        childRequest.setResponseParser(new InputStreamResponseParser("json"));

        return executeJson(solr, childRequest).getJSONObject("response").getJSONArray("docs");
    }

    private static JSONObject executeJson(SolrClient solr, JsonQueryRequest request) throws Exception {
        NamedList<Object> response = solr.request(request, TITLES_CORE);
        InputStream stream = (InputStream) response.get("stream");
        return new JSONObject(IOUtils.toString(stream, "UTF-8"));
    }

    private static JsonQueryRequest addSimpleMonographsFilter(JsonQueryRequest request) {
        return request
                .withFilter("model:monograph")
                .withFilter("-state:(PARENT_OPEN OR PARENT_CLOSED)");
    }

    private static JsonQueryRequest addDateRangeFilter(JsonQueryRequest request, String from, String to) {
        if (from == null && to == null) {
            return request;
        }
        String start = from == null ? "*" : toSolrDate(from);
        String end = to == null ? "*" : toSolrDate(to);
        return request.withFilter("state_date:[" + start + " TO " + end + "]");
    }

    private static JsonQueryRequest addMultiValueFilter(JsonQueryRequest request, String field, List<String> values) {
        if (values == null || values.isEmpty()) {
            return request;
        }

        StringBuilder filter = new StringBuilder(field).append(":(");
        boolean hasValue = false;
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (hasValue) {
                filter.append(" OR ");
            }
            filter.append("\"").append(ClientUtils.escapeQueryChars(value.trim())).append("\"");
            hasValue = true;
        }
        filter.append(")");

        return hasValue ? request.withFilter(filter.toString()) : request;
    }

    private static ChangeType normalizeChangeType(String type) {
        if (type == null || type.isBlank()) {
            throw new WebApplicationException(error("MISSING_TYPE",
                    "Path parameter type is required. Supported values are simple-monographs and sets."),
                    Response.Status.BAD_REQUEST);
        }

        String normalized = type.trim().toLowerCase(Locale.ROOT);
        return ChangeType.fromPathValue(normalized);
    }

    private static String normalizeLibrary(String library) {
        if (library == null || library.isBlank()) {
            throw new WebApplicationException(error("MISSING_LIBRARY", "Query parameter library is required."),
                    Response.Status.BAD_REQUEST);
        }

        String normalized = library.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LIBRARIES.contains(normalized)) {
            throw new WebApplicationException(error("UNSUPPORTED_LIBRARY", "Supported library values are mzk and nkp."),
                    Response.Status.BAD_REQUEST);
        }
        return normalized;
    }

    private static int parseBoundedInt(String name, String rawValue, int defaultValue, int min, int max) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue);
            if (value < min || value > max) {
                throw new WebApplicationException(error("INVALID_" + name.toUpperCase(Locale.ROOT),
                        name + " must be between " + min + " and " + max + "."),
                        Response.Status.BAD_REQUEST);
            }
            return value;
        } catch (NumberFormatException ex) {
            throw new WebApplicationException(error("INVALID_" + name.toUpperCase(Locale.ROOT),
                    name + " must be a number."), Response.Status.BAD_REQUEST);
        }
    }

    private static String normalizeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return INITIAL_CURSOR;
        }
        return cursor.trim();
    }

    private static void validateDate(String name, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!SOLR_DATE_PATTERN.matcher(value.trim()).matches()) {
            throw new WebApplicationException(error("INVALID_" + name.toUpperCase(Locale.ROOT),
                    name + " must use yyyy-MM-dd or yyyy-MM-ddTHH:mm:ss.SSSZ."),
                    Response.Status.BAD_REQUEST);
        }
    }

    private static String toSolrDate(String value) {
        String trimmed = value.trim();
        if (trimmed.length() == 10) {
            return trimmed + "T00:00:00.000Z";
        }
        if (trimmed.length() == 20) {
            return trimmed.substring(0, 19) + ".000Z";
        }
        return trimmed;
    }

    private static String error(String code, String message) {
        return new JSONObject()
                .put("error", code)
                .put("message", message)
                .toString(2);
    }

    private enum ChangeType {
        SIMPLE_MONOGRAPHS("simple-monographs"),
        SETS("sets");

        private final String pathValue;

        ChangeType(String pathValue) {
            this.pathValue = pathValue;
        }

        private static ChangeType fromPathValue(String pathValue) {
            for (ChangeType type : values()) {
                if (type.pathValue.equals(pathValue)) {
                    return type;
                }
            }
            throw new WebApplicationException(error("UNSUPPORTED_TYPE",
                    "Supported type values are simple-monographs and sets."),
                    Response.Status.BAD_REQUEST);
        }
    }
}
