package cz.inovatika.workflow;

import cz.inovatika.conf.WorkflowUpdateConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Best-effort client for the targeted workflow recalculation endpoint.
 */
public final class WorkflowUpdaterClient {

    private static final Logger LOGGER = Logger.getLogger(WorkflowUpdaterClient.class.getName());
    private static final String PATH = "/internal/workflow/recalculate";

    private WorkflowUpdaterClient() {
    }

    public static CompletableFuture<Void> requestUpdates(
            HttpClient httpClient,
            WorkflowUpdateConfig config,
            Collection<String> pids,
            String requestedBy,
            String cause
    ) {
        if (!config.isEnabled()) {
            return CompletableFuture.completedFuture(null);
        }

        LinkedHashSet<String> uniquePids = new LinkedHashSet<>();
        for (String pid : pids) {
            if (pid != null && !pid.isBlank()) {
                uniquePids.add(pid.trim());
            }
        }
        if (uniquePids.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            List<String> values = new ArrayList<>(uniquePids);
            List<CompletableFuture<Void>> requests = new ArrayList<>();
            int batchSize = config.getMaxBatchSize();
            for (int start = 0; start < values.size(); start += batchSize) {
                requests.add(send(
                        httpClient,
                        config,
                        values.subList(start, Math.min(values.size(), start + batchSize)),
                        requestedBy,
                        cause
                ));
            }
            return CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new));
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Unable to submit targeted workflow updates for {0}: {1}",
                    new Object[]{uniquePids, ex.getMessage()}
            );
            return CompletableFuture.completedFuture(null);
        }
    }

    private static CompletableFuture<Void> send(
            HttpClient httpClient,
            WorkflowUpdateConfig config,
            List<String> pids,
            String requestedBy,
            String cause
    ) {
        String baseUrl = config.getUrl().replaceAll("/+$", "");
        JSONObject body = new JSONObject()
                .put("pids", new JSONArray(pids))
                .put("requestedBy", requestedBy)
                .put("cause", cause);

        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + PATH))
                .timeout(Duration.ofSeconds(config.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (!config.getToken().isBlank()) {
            request.header("Authorization", "Bearer " + config.getToken());
        }

        return httpClient.sendAsync(request.build(), HttpResponse.BodyHandlers.discarding())
                .handle((response, failure) -> {
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
                    return null;
                });
    }
}
