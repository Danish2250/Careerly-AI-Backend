
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
            return "Gemini API key is missing. Add GEMINI_API_KEY in Render backend Environment settings.";
        }

        String safePrompt = prompt == null ? "" : prompt;

        String body = """
            {
              "systemInstruction": {
                "parts": [{
                  "text": "You are Careerly.ai, a practical and supportive career coach. Give specific, honest, beginner-friendly advice. Help with software engineering placements, Java, DSA, resumes, interviews, and career roadmaps. Do not invent credentials, job openings, or resume facts."
                }]
              },
              "contents": [{
                "parts": [{
                  "text": %s
                }]
              }],
              "generationConfig": {
                "temperature": 0.4,
                "maxOutputTokens": 1200
              }
            }
            """.formatted(jsonString(safePrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
                    ))
                    .timeout(Duration.ofSeconds(60))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                System.err.println("Gemini API HTTP " + response.statusCode()
                        + ": " + response.body());

                return "Gemini API error (HTTP " + response.statusCode()
                        + "). Check Render logs, API key, model access, and usage limits.";
            }

            String answer = extractText(response.body());

            if (answer.isBlank()) {
                return "Gemini returned an empty response. Please try again.";
            }

            return answer;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "The AI request was interrupted. Please try again.";
        } catch (Exception e) {
            System.err.println("Gemini request failed: " + e.getMessage());
            return "Could not reach Gemini AI. Please check the backend logs.";
        }
    }

    private String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }

        return out.append('"').toString();
    }

    private String extractText(String json) {
        String marker = "\"text\"";
        int position = json.indexOf("\"candidates\"");
        if (position < 0) return "";

        int end = json.indexOf("\"finishReason\"", position);
        if (end < 0) end = json.length();

        String candidate = json.substring(position, end);
        int markerPosition = candidate.indexOf(marker);

        if (markerPosition < 0) return "";

        int colon = candidate.indexOf(':', markerPosition + marker.length());
        if (colon < 0) return "";

        int start = candidate.indexOf('"', colon + 1);
        if (start < 0) return "";

        StringBuilder out = new StringBuilder();
        boolean escape = false;

        for (int i = start + 1; i < candidate.length(); i++) {
            char c = candidate.charAt(i);

            if (escape) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'u' -> {
                        if (i + 4 < candidate.length()) {
                            try {
                                out.append((char) Integer.parseInt(
                                        candidate.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                return "";
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
