/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.nkp.kuapp.kuappweb;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 *
 * @author alber
 */
public class GeminiAnalyzer {

    public static JSONObject analyze(String base64Pdf) throws IOException, InterruptedException {
        JSONObject jsonRequest = new JSONObject();
        JSONObject geminiConfig = Options.getInstance().getJSONObject("gemini");
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "application/pdf").put("data", base64Pdf)));
        parts.put(new JSONObject().put("text", geminiConfig.getString("text")));
        jsonRequest.put("model", "gemini-2.5-flash");
        jsonRequest.put("contents", new JSONObject().put("parts", parts));
        jsonRequest.put("generationConfig", geminiConfig.getJSONObject("config"));

        // 3. Send REST request using Java HttpClient
        String apiKey = geminiConfig.optString("api_key", "").trim();
        if (apiKey.isEmpty() || apiKey.toLowerCase().contains("nastavit")) {
            throw new IllegalStateException(
                "Gemini API key is missing or is still a placeholder. Configure server.gemini.api_key in .kapp/config.json.");
        }
        String endpoint = geminiConfig.getString("url")
            + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest restRequest = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest.toString()))
                .build();

        HttpResponse<String> restResponse = client.send(restRequest, HttpResponse.BodyHandlers.ofString());
        if (restResponse.statusCode() < 200 || restResponse.statusCode() >= 300) {
            throw new IllegalStateException("Gemini API returned HTTP " + restResponse.statusCode()
                + ": " + restResponse.body());
        }
        return new JSONObject(restResponse.body());
    }
}
