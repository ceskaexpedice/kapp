package cz.inovatika.conf;

public class WorkflowUpdateConfig {

    private int port = 8081;
    private String token = "";
    private int maxBatchSize = 100;
    private int responseTimeoutSeconds = 30;

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token == null ? "" : token;
    }

    public int getMaxBatchSize() {
        return maxBatchSize > 0 ? maxBatchSize : 100;
    }

    public int getResponseTimeoutSeconds() {
        return responseTimeoutSeconds > 0 ? responseTimeoutSeconds : 30;
    }
}
