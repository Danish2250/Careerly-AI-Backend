package com.careerly.service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
public class AiService {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    public String ask(String prompt) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            return "AI is not configured yet. Set the OPENAI_API_KEY environment variable, restart the backend, and try again. Your request was received: " + prompt;
        }

        String safePrompt = prompt == null ? "" : prompt;
        String body = """
            {
              "model": "gpt-4.1-mini",
              "messages": [
                {"role":"system","content":"You are Careerly.ai, a practical and supportive career coach. Give specific, honest, beginner-friendly advice. Do not invent credentials, job openings, or resume facts."},
                {"role":"user","content":%s}
              ],
              "temperature":0.4
            }
            """.formatted(jsonString(safePrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "AI provider returned HTTP " + response.statusCode() + ". Check your API key, account access, and billing.";
            }
            return extractContent(response.body());
        } catch (Exception e) {
            return "Could not reach the AI service. Check your internet connection and API configuration.";
        }
    }

    private String jsonString(String value) {
        String escaped = value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "\"" + escaped + "\"";
    }

    // Small JSON response extractor for the expected chat-completions response.
    // For production, replace this with Jackson DTOs and structured error handling.
    private String extractContent(String json) {
        String marker = "\"content\":\"";
        int start = json.indexOf(marker);
        if (start < 0) return "AI response received, but the response format could not be read.";
        start += marker.length();
        StringBuilder out = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    default -> out.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                break;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
