package cz.inovatika.conf;

public class WorkflowUpdateConfig {

    private boolean enabled = true;
    private String url = "http://updater:8081";
    private int port = 8081;
    private String token = "";
    private int maxBatchSize = 100;
    private int requestTimeoutSeconds = 35;
    private int responseTimeoutSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public String getUrl() {
        return url == null || url.isBlank() ? "http://updater:8081" : url;
    }

    public int getPort() {
        return port;
    }

    public String getToken() {
        return token == null ? "" : token;
    }

    public int getMaxBatchSize() {
        return maxBatchSize > 0 ? maxBatchSize : 100;
    }

    public int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds > 0 ? requestTimeoutSeconds : 35;
    }

    public int getResponseTimeoutSeconds() {
        return responseTimeoutSeconds > 0 ? responseTimeoutSeconds : 30;
    }
}
