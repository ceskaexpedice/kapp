/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package cz.nkp.kuapp.kuappweb;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest restRequest = HttpRequest.newBuilder()
                .uri(URI.create(geminiConfig.getString("url") + geminiConfig.getString("api_key")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest.toString()))
                .build();

        HttpResponse<String> restResponse = client.send(restRequest, HttpResponse.BodyHandlers.ofString());
        return new JSONObject(restResponse.body());
    }
}
