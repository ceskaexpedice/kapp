package cz.inovatika;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cz.inovatika.Updater.TitleUpdateResult;
import cz.inovatika.conf.WorkflowUpdateConfig;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class WorkflowUpdateServer implements AutoCloseable {

    private static final int MAX_REQUEST_BYTES = 64 * 1024;
    private static final String PATH = "/internal/workflow/recalculate";

    private final WorkflowUpdateCoordinator coordinator;
    private final WorkflowUpdateConfig config;
    private final HttpServer server;

    public WorkflowUpdateServer(
            WorkflowUpdateCoordinator coordinator,
            WorkflowUpdateConfig config
    ) throws IOException {
        this.coordinator = coordinator;
        this.config = config;
        this.server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        this.server.createContext(PATH, this::handle);
        this.server.setExecutor(Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "workflow-update-http");
            thread.setDaemon(true);
            return thread;
        }));
    }

    public void start() {
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.getResponseHeaders().set("Allow", "POST");
                writeJson(exchange, 405, error("method_not_allowed", "Only POST is supported."));
                return;
            }
            if (!isAuthorized(exchange)) {
                writeJson(exchange, 401, error("unauthorized", "Invalid internal API token."));
                return;
            }

            byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
            if (body.length > MAX_REQUEST_BYTES) {
                writeJson(exchange, 413, error("request_too_large", "Request body is too large."));
                return;
            }

            List<String> pids;
            try {
                pids = parsePids(new JSONObject(new String(body, StandardCharsets.UTF_8)));
            } catch (IllegalArgumentException | JSONException ex) {
                writeJson(exchange, 400, error("invalid_request", ex.getMessage()));
                return;
            }

            var future = coordinator.updatePids(pids);
            try {
                List<TitleUpdateResult> results = future.get(
                        config.getResponseTimeoutSeconds(),
                        TimeUnit.SECONDS
                );
                writeJson(exchange, 200, resultsJson(results));
            } catch (TimeoutException ex) {
                writeJson(exchange, 202, new JSONObject()
                        .put("status", "queued")
                        .put("pids", new JSONArray(pids)));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                writeJson(exchange, 503, error("interrupted", "Workflow update was interrupted."));
            } catch (ExecutionException ex) {
                writeJson(exchange, 500, error(
                        "update_failed",
                        ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()
                ));
            }
        }
    }

    private List<String> parsePids(JSONObject request) {
        JSONArray values = request.optJSONArray("pids");
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("At least one PID is required.");
        }

        LinkedHashSet<String> uniquePids = new LinkedHashSet<>();
        for (int i = 0; i < values.length(); i++) {
            String pid = values.optString(i, "").trim();
            if (pid.isBlank()) {
                throw new IllegalArgumentException("PIDs must not be blank.");
            }
            uniquePids.add(pid);
        }
        if (uniquePids.size() > config.getMaxBatchSize()) {
            throw new IllegalArgumentException(
                    "At most " + config.getMaxBatchSize() + " PIDs are allowed."
            );
        }
        return new ArrayList<>(uniquePids);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String expectedToken = config.getToken();
        if (expectedToken.isBlank()) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String suppliedToken = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring("Bearer ".length())
                : "";
        return MessageDigest.isEqual(
                expectedToken.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static JSONObject resultsJson(List<TitleUpdateResult> results) {
        JSONArray values = new JSONArray();
        for (TitleUpdateResult result : results) {
            values.put(new JSONObject()
                    .put("pid", result.pid())
                    .put("status", result.status())
                    .put("previousState", result.previousState())
                    .put("newState", result.newState())
                    .put("message", result.message()));
        }
        return new JSONObject().put("results", values);
    }

    private static JSONObject error(String code, String message) {
        return new JSONObject()
                .put("code", code)
                .put("message", message == null ? JSONObject.NULL : message);
    }

    private static void writeJson(HttpExchange exchange, int status, JSONObject body)
            throws IOException {
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(1);
    }
}
