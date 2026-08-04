package cz.inovatika.workflow;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowUpdateConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkflowUpdaterClientTest {

    @Test
    void submitsUniquePidsInConfiguredBatches() throws Exception {
        List<JSONObject> requests = new ArrayList<>();
        List<String> authorizationHeaders = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/workflow/recalculate", exchange ->
                handle(exchange, requests, authorizationHeaders));
        server.start();

        try {
            WorkflowUpdateConfig config = config("""
                    {
                      "workflowUpdate": {
                        "url": "http://127.0.0.1:%d/",
                        "token": "secret",
                        "maxBatchSize": 2,
                        "requestTimeoutSeconds": 5
                      }
                    }
                    """.formatted(server.getAddress().getPort()));

            WorkflowUpdaterClient.requestUpdates(
                    HttpClient.newHttpClient(),
                    config,
                    List.of("uuid:first", "uuid:second", "uuid:first", " ", "uuid:third"),
                    "importer",
                    "harvest:nkp"
            ).get(10, TimeUnit.SECONDS);

            Assertions.assertEquals(2, requests.size());
            List<List<String>> batches = requests.stream()
                    .map(request -> request.getJSONArray("pids").toList().stream()
                            .map(Object::toString)
                            .toList())
                    .toList();
            Assertions.assertTrue(batches.contains(List.of("uuid:first", "uuid:second")));
            Assertions.assertTrue(batches.contains(List.of("uuid:third")));
            Assertions.assertTrue(requests.stream().allMatch(request ->
                    "importer".equals(request.getString("requestedBy"))
                            && "harvest:nkp".equals(request.getString("cause"))));
            Assertions.assertEquals(List.of("Bearer secret", "Bearer secret"), authorizationHeaders);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void skipsRequestsWhenDisabled() throws Exception {
        WorkflowUpdateConfig config = config("""
                {"workflowUpdate":{"enabled":false,"url":"not a URI"}}
                """);

        WorkflowUpdaterClient.requestUpdates(
                HttpClient.newHttpClient(),
                config,
                List.of("uuid:test"),
                "importer",
                "harvest:nkp"
        ).get(1, TimeUnit.SECONDS);
    }

    private static WorkflowUpdateConfig config(String json) {
        return ApplicationConfig.loadFromStream(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)
        )).workflowUpdateOrDefault();
    }

    private static void handle(
            HttpExchange exchange,
            List<JSONObject> requests,
            List<String> authorizationHeaders
    ) throws IOException {
        try (exchange) {
            synchronized (requests) {
                requests.add(new JSONObject(new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8
                )));
                authorizationHeaders.add(exchange.getRequestHeaders().getFirst("Authorization"));
            }
            exchange.sendResponseHeaders(202, -1);
        }
    }
}
