package com.airnovation.aivoicekeyboard;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** REST-based message editing. No credential is embedded into the application binary. */
public final class GeminiEnglish {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Older 2.5 models may be unavailable to newly created Gemini API projects.
    // Prefer low-cost, current models; fail over only when a model returns 404.
    private static final String[] MODELS = {
            "gemini-3.5-flash-lite",
            "gemini-3.8-flash",
            "gemini-3.6-flash"
    };

    public interface Callback { void done(String result, String error); }
    private GeminiEnglish() { }

    public static void rewrite(String apiKey, String original, Callback callback) {
        EXECUTOR.execute(() -> {
            String output = null;
            String error = null;
            try {
                byte[] payload = buildRequest(original).toString().getBytes(StandardCharsets.UTF_8);
                for (String model : MODELS) {
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                                + model + ":generateContent");
                        conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(40000);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("x-goog-api-key", apiKey);
                        conn.setRequestProperty("Content-Type", "application/json");
                        try (OutputStream stream = conn.getOutputStream()) { stream.write(payload); }

                        int status = conn.getResponseCode();
                        String response = readResponse(status >= 200 && status < 300
                                ? conn.getInputStream() : conn.getErrorStream());
                        if (status == 404) {
                            // A 404 can mean a model is not accessible for this project.
                            continue;
                        }
                        if (status < 200 || status >= 300) {
                            String category = errorCategory(response);
                            throw new Exception("Gemini HTTP " + status + " (" + category + ") using "
                                    + model + ". Check Google AI Studio API access, quota or billing.");
                        }
                        output = parseText(response);
                        if (output.isEmpty()) {
                            throw new Exception("Gemini responded without text (" + model + ").");
                        }
                        break;
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
                if (output == null) {
                    throw new Exception("Gemini HTTP 404: none of the supported models are accessible "
                            + "(3.5 Flash-Lite, 3.8 Flash or 3.6 Flash). Check the API Key's "
                            + "Google AI Studio project/model access.");
                }
            } catch (Exception e) {
                // Do not log the key, prompt, response body or any confidential message.
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            }
            final String resultFinal = output, errorFinal = error;
            MAIN.post(() -> callback.done(resultFinal, errorFinal));
        });
    }

    private static JSONObject buildRequest(String original) throws Exception {
        String instruction = "You are editing a short workplace message for a Singapore engineering operations manager. "
                + "Translate Chinese, Malay, or mixed-language speech into natural, clear, concise English. "
                + "Use friendly, professional everyday wording suitable for WhatsApp and email. "
                + "Preserve meaning exactly: all numbers, measurement units, technical terminology, names, "
                + "constraints, uncertainty and commitments. "
                + "Use engineering terms including Anchor, Unit Cooler, Condensing Unit, Method Statement, ASRS. "
                + "Never invent promises or facts. Return only the English message, without commentary.";
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", instruction))));
        JSONObject userContent = new JSONObject().put("role", "user").put("parts",
                new JSONArray().put(new JSONObject().put("text", original)));
        body.put("contents", new JSONArray().put(userContent));
        body.put("generationConfig", new JSONObject().put("temperature", 0.2)
                .put("maxOutputTokens", 1024));
        return body;
    }

    private static String readResponse(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private static String errorCategory(String response) {
        try {
            JSONObject e = new JSONObject(response).optJSONObject("error");
            if (e != null) {
                String status = e.optString("status", "");
                // A coarse error status is enough for troubleshooting.
                if (status.matches("[A-Z_]{2,40}")) return status;
            }
        } catch (Exception ignored) { }
        return "UNKNOWN";
    }

    private static String parseText(String response) throws Exception {
        JSONObject result = new JSONObject(response);
        JSONArray candidates = result.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new Exception("Gemini returned no candidate. The prompt may have been blocked.");
        }
        JSONObject candidate = candidates.optJSONObject(0);
        if (candidate == null) return "";
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder text = new StringBuilder();
        if (parts != null) for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part != null && part.has("text")) text.append(part.optString("text"));
        }
        return text.toString().trim();
    }
}
