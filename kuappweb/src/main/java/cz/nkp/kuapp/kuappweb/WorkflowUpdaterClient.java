package cz.nkp.kuapp.kuappweb;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Best-effort internal client for targeted workflow recalculation.
 */
public final class WorkflowUpdaterClient {

    private static final Logger LOGGER = Logger.getLogger(WorkflowUpdaterClient.class.getName());
    private static final String DEFAULT_URL = "http://updater:8081";
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private WorkflowUpdaterClient() {
    }

    public static void requestUpdate(String pid, String requestedBy, String cause) {
        requestUpdates(List.of(pid), requestedBy, cause);
    }

    public static void requestUpdates(
            Collection<String> pids,
            String requestedBy,
            String cause
    ) {
        try {
            requestUpdatesInternal(pids, requestedBy, cause);
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to submit targeted workflow update for {0}: {1}",
                    new Object[]{pids, ex.getMessage()}
            );
        }
    }

    private static void requestUpdatesInternal(
            Collection<String> pids,
            String requestedBy,
            String cause
    ) {
        JSONObject config = workflowUpdateConfig();
        if (!config.optBoolean("enabled", true)) {
            return;
        }

        LinkedHashSet<String> uniquePids = new LinkedHashSet<>();
        for (String pid : pids) {
            if (pid != null && !pid.isBlank()) {
                uniquePids.add(pid.trim());
            }
        }
        if (uniquePids.isEmpty()) {
            return;
        }

        int batchSize = Math.max(1, config.optInt("maxBatchSize", DEFAULT_BATCH_SIZE));
        List<String> values = new ArrayList<>(uniquePids);
        for (int start = 0; start < values.size(); start += batchSize) {
            send(
                    values.subList(start, Math.min(values.size(), start + batchSize)),
                    requestedBy,
                    cause,
                    config
            );
        }
    }

    private static void send(
            List<String> pids,
            String requestedBy,
            String cause,
            JSONObject config
    ) {
        String baseUrl = config.optString("url", DEFAULT_URL).replaceAll("/+$", "");
        JSONObject body = new JSONObject()
                .put("pids", new JSONArray(pids))
                .put("requestedBy", requestedBy)
                .put("cause", cause);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/internal/workflow/recalculate"))
                .timeout(Duration.ofSeconds(config.optInt("requestTimeoutSeconds", 35)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String token = config.optString("token", "");
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }

        HTTP_CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((response, failure) -> {
                    if (failure != null) {
                        LOGGER.log(
                                Level.WARNING,
                                "Targeted workflow update request failed for {0}: {1}",
                                new Object[]{pids, failure.getMessage()}
                        );
                    } else if (response.statusCode() != 200 && response.statusCode() != 202) {
                        LOGGER.log(
                                Level.WARNING,
                                "Targeted workflow update request for {0} returned HTTP {1}",
                                new Object[]{pids, response.statusCode()}
                        );
                    }
                });
    }

    private static JSONObject workflowUpdateConfig() {
        JSONObject app = Options.getInstance().getJSONObject("app");
        JSONObject config = app == null ? null : app.optJSONObject("workflowUpdate");
        return config == null ? new JSONObject() : config;
    }
}
