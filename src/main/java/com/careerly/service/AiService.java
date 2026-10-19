
package com.careerly.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String ask(String prompt) {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            return "Gemini AI is not configured. Please set GEMINI_API_KEY in Render.";
        }

        String safePrompt = prompt == null ? "" : prompt;

        String body = """
            {
              "systemInstruction": {
                "parts": [{
                  "text": "You are Careerly.ai, a practical and supportive career coach. Give specific, honest, beginner-friendly career advice. Do not invent job openings or resume facts."
                }]
              },
              "contents": [{
                "parts": [{
                  "text": %s
                }]
              }],
              "generationConfig": {
                "temperature": 0.4
              }
            }
            """.formatted(jsonString(safePrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return "Gemini AI error (HTTP " + response.statusCode()
                        + "). Check your API key and Google AI Studio access.";
            }

            return extractText(response.body());

        } catch (Exception e) {
            return "Could not connect to Gemini AI. Please try again.";
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

    private String extractText(String json) {
        String marker = "\"text\":";
        int start = json.indexOf(marker);

        if (start < 0) {
            return "Gemini returned a response that could not be read.";
        }

        start += marker.length();

        while (start < json.length()
                && Character.isWhitespace(json.charAt(start))) {
            start++;
        }

        if (start >= json.length() || json.charAt(start) != '"') {
            return "Gemini returned an unexpected response.";
        }

        start++;
        StringBuilder out = new StringBuilder();
        boolean escape = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(
                                        json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append('u');
                            }
                        }
                    }
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
