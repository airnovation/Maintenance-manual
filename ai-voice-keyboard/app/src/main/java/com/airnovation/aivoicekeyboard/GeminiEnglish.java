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

/** Gemini REST translation; called only after the user presses AI English. */
public final class GeminiEnglish {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Callback { void done(String result, String error); }
    private GeminiEnglish() { }

    public static void rewrite(String apiKey, String original, Callback callback) {
        EXECUTOR.execute(() -> {
            String output = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent");
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(40000);
                connection.setDoOutput(true);
                connection.setRequestProperty("x-goog-api-key", apiKey);
                connection.setRequestProperty("Content-Type", "application/json");

                String instruction = "You are editing a short workplace message for a Singapore engineering operations manager. "
                        + "Translate Chinese, Malay, or mixed-language speech into natural, clear, concise English. "
                        + "Use friendly, professional everyday wording suitable for WhatsApp and email. "
                        + "Preserve meaning exactly: all numbers, measurement units, technical terminology, names, constraints, uncertainty and commitments. "
                        + "Use proper engineering terms including Anchor, Unit Cooler, Condensing Unit, Method Statement, ASRS. "
                        + "Never invent promises or facts. Return only the final English message, without commentary or quotation marks.";

                JSONObject body = new JSONObject();
                JSONObject instructionPart = new JSONObject().put("text", instruction);
                body.put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(instructionPart)));
                JSONObject userPart = new JSONObject().put("text", original);
                JSONObject userContent = new JSONObject()
                        .put("role", "user")
                        .put("parts", new JSONArray().put(userPart));
                body.put("contents", new JSONArray().put(userContent));
                body.put("generationConfig", new JSONObject().put("temperature", 0.2));
                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(payload); }

                int status = connection.getResponseCode();
                InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }
                if (status < 200 || status >= 300) {
                    throw new Exception("Gemini API HTTP " + status + ". Check Gemini key, billing, access or network.");
                }

                JSONObject result = new JSONObject(response.toString());
                JSONArray candidates = result.optJSONArray("candidates");
                if (candidates == null || candidates.length() == 0) throw new Exception("Gemini returned no candidates");
                JSONObject candidate = candidates.optJSONObject(0);
                if (candidate == null) throw new Exception("Gemini returned an empty result");
                JSONArray parts = candidate.optJSONObject("content") == null
                        ? null : candidate.getJSONObject("content").optJSONArray("parts");
                StringBuilder text = new StringBuilder();
                if (parts != null) for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null && part.has("text")) text.append(part.optString("text"));
                }
                output = text.toString().trim();
                if (output.isEmpty()) throw new Exception("Gemini returned no message text");
            } catch (Exception exception) {
                error = exception.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            String finalOutput = output, finalError = error;
            MAIN.post(() -> callback.done(finalOutput, finalError));
        });
    }
}
