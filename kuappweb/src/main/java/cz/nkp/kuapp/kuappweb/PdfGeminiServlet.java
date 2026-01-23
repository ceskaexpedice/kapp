/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/JSP_Servlet/Servlet.java to edit this template
 */
package cz.nkp.kuapp.kuappweb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.google.protobuf.ByteString;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@WebServlet("/analyze-pdf")
@MultipartConfig(
    fileSizeThreshold = 1024 * 1024 * 1,  // 1 MB
    maxFileSize = 1024 * 1024 * 10,       // 10 MB
    maxRequestSize = 1024 * 1024 * 15     // 15 MB
)
public class PdfGeminiServlet extends HttpServlet {

    private final String PROJECT_ID = "your-gcp-project-id";
    private final String LOCATION = "us-central1";
    private final String MODEL_NAME = "gemini-1.5-flash"; // Flash is best for extraction tasks

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // 1. Set response type to JSON
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // 2. Extract PDF from Multipart request
            javax.servlet.http.Part filePart = request.getPart("file");
            if (filePart == null || !filePart.getContentType().equalsIgnoreCase("application/pdf")) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\": \"Valid PDF file is required\"}");
                return;
            }

            byte[] pdfBytes;
            try (InputStream is = filePart.getInputStream()) {
                pdfBytes = is.readAllBytes();
            }

            // 3. Initialize Gemini and Generate Content
            String geminiJsonResult = processWithGemini(pdfBytes);

            // 4. Return the result
            response.getWriter().write(geminiJsonResult);

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private String processWithGemini(byte[] pdfBytes) throws Exception {
        try (VertexAI vertexAi = new VertexAI(PROJECT_ID, LOCATION)) {
            GenerativeModel model = new GenerativeModel(MODEL_NAME, vertexAi);

            // Define the prompt for structured JSON output
            String promptText = "Analyze the provided PDF. Extract the metadata (Author, Creation Date, Title) " +
                                "and provide a concise summary. Return the response ONLY as a valid JSON object " +
                                "with keys: 'author', 'date', 'title', 'summary'.";

            // Create multimodal content (Text + PDF Bytes)
            Content content = Content.newBuilder()
                .addParts(Part.newBuilder().setText(promptText))
                .addParts(Part.newBuilder().setInlineData(
                    com.google.cloud.vertexai.api.Blob.newBuilder()
                        .setMimeType("application/pdf")
                        .setData(ByteString.copyFrom(pdfBytes))
                        .build()
                ))
                .build();

            GenerateContentResponse genResponse = model.generateContent(content);
            String rawText = ResponseHandler.getText(genResponse);

            // Clean up Markdown formatting (strip ```json ... ```) if Gemini adds it
            return cleanJsonResponse(rawText);
        }
    }

    private String cleanJsonResponse(String raw) {
        return raw.replaceAll("```json", "").replaceAll("```", "").trim();
    }
}