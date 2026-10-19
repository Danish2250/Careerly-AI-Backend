
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
            System.err.println("GEMINI_API_KEY is missing.");
            return "Gemini API key is missing. Check Render environment variables.";
        }

        String safePrompt = prompt == null ? "" : prompt;

        String body = """
                {
                  "systemInstruction": {
                    "parts": [{
                      "text": "You are Careerly.ai, a helpful career coach. Give practical, beginner-friendly advice for Java, DSA, resumes, and software engineering placements."
                    }]
                  },
                  "contents": [{
                    "parts": [{
                      "text": %s
                    }]
                  }]
                }
                """.formatted(jsonString(safePrompt));

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
                    .timeout(Duration.ofSeconds(60))
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {

                System.err.println(
                        "Gemini HTTP status: " + response.statusCode());
                System.err.println(
                        "Gemini error response: " + response.body());

                return "Gemini AI error (HTTP "
                        + response.statusCode()
                        + "). Check Render Logs for details.";
            }

            String result = extractText(response.body());

            if (result.isBlank()) {
                return "Gemini returned an empty response. Please try again.";
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "AI request interrupted. Please try again.";

        } catch (Exception e) {
            System.err.println("Gemini connection error: " + e.getMessage());
            return "Could not connect to Gemini AI. Please try again.";
        }
    }

    private String jsonString(String value) {

        StringBuilder escaped = new StringBuilder("\"");

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);

            switch (c) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");

                default -> {
                    if (c < 0x20) {
                        escaped.append(
                                String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
                }
            }
        }

        return escaped.append('"').toString();
    }

    private String extractText(String json) {

        String marker = "\"text\":";
        int start = json.indexOf(marker);

        if (start < 0) {
            System.err.println("Gemini response contains no text field.");
            System.err.println("Gemini response: " + json);
            return "Gemini returned an unreadable response.";
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
                return out.toString();

            } else {
                out.append(c);
            }
        }

        return out.toString();
    }
}
