
package cz.nkp.kuapp.kuappweb;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

@WebServlet("/extract-pdf")
@MultipartConfig(location = "/tmp", fileSizeThreshold = 1024 * 1024 * 20,
        maxFileSize = 1024 * 1024 * 20, // 20MB
        maxRequestSize = 1024 * 1024 * 50)   // 50MB)  
public class GeminiServlet extends HttpServlet {
    public static final Logger LOGGER = Logger.getLogger(GeminiServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");

        try {
            // 1. Get PDF from request
            Part filePart = request.getPart("file");
            LOGGER.log(Level.INFO, "Part name -> {0}", filePart.getName());
            byte[] pdfBytes = filePart.getInputStream().readAllBytes();
            String base64Pdf = Base64.getEncoder().encodeToString(pdfBytes);

            // 2. Build the JSON request body for Gemini
            // We use 'response_mime_type': 'application/json' to force a valid JSON response
            
            JSONObject jsonRequest = new JSONObject();
            
   /**
    * 
     {
      model: 'gemini-3-flash-preview',
      contents: {
        parts: [
          { inlineData: { mimeType: 'application/pdf', data: pdfBase64 } },
          { text: `Analyze this PDF and extract metadata. Return JSON with: title, author, creationDate, keywords (array), summary (3-sentence), pageCount, language.` }
        ]
      },
      config: {
        responseMimeType: "application/json",
        responseSchema: {
          type: Type.OBJECT,
          properties: {
            title: { type: Type.STRING },
            author: { type: Type.STRING },
            creationDate: { type: Type.STRING },
            keywords: { type: Type.ARRAY, items: { type: Type.STRING } },
            summary: { type: Type.STRING },
            pageCount: { type: Type.NUMBER },
            language: { type: Type.STRING },
          },
          required: ["title", "author", "creationDate", "summary"],
        }
      }
     }
      
    */      
      
            JSONObject geminiConfig = Options.getInstance().getJSONObject("gemini");
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("inlineData", new JSONObject().put("mimeType", "application/pdf").put("data", base64Pdf)));
            parts.put( new JSONObject().put("text", geminiConfig.getString("text")) );
            jsonRequest.put("model", "gemini-2-flash-preview");
            jsonRequest.put("contents", new JSONObject().put("parts", parts));
            jsonRequest.put("config", geminiConfig.getJSONArray("config"));

            // 3. Send REST request using Java HttpClient
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest restRequest = HttpRequest.newBuilder()
                .uri(URI.create(geminiConfig.getString("url") + geminiConfig.getString("api_key")))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest.toString()))
                .build();

            HttpResponse<String> restResponse = client.send(restRequest, HttpResponse.BodyHandlers.ofString());

            // 4. Parse Gemini's response and send back to client
            //String aiResult = extractAiText(restResponse.body());
            response.getWriter().write(restResponse.body());

        } catch (Exception e) {
            response.setStatus(500);
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

}