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

public final class OpenAiEnglish {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Callback { void done(String result, String error); }
    private OpenAiEnglish() {}

    public static void rewrite(String apiKey, String original, Callback callback) {
        EXECUTOR.execute(() -> {
            String output = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(40000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Content-Type", "application/json");
                JSONObject body = new JSONObject();
                body.put("model", "gpt-4.1-mini");
                body.put("store", false);
                body.put("instructions", "You are a professional workplace English message editor for a Singapore engineering operations manager. Translate any Chinese, Malay, or mixed speech into natural, clear, concise conversational English. Preserve the exact meaning, names, numbers, measurements, commitments, uncertainty, and technical terms (e.g. Anchor, Unit Cooler, Condensing Unit, Method Statement). Never invent facts, dates, approvals, or promises. Return ONLY the message text, no commentary, greetings or quotation marks unless the speaker asked for them.");
                body.put("input", original);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream stream = connection.getOutputStream()) { stream.write(bytes); }
                int status = connection.getResponseCode();
                InputStream response = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder received = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) received.append(line);
                }
                if (status < 200 || status >= 300) throw new Exception("API HTTP " + status + " (check API key, billing or network)");
                JSONObject json = new JSONObject(received.toString());
                JSONArray items = json.optJSONArray("output");
                StringBuilder text = new StringBuilder();
                if (items != null) for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type"))) continue;
                    JSONArray contents = item.optJSONArray("content");
                    if (contents == null) continue;
                    for (int j = 0; j < contents.length(); j++) {
                        JSONObject c = contents.optJSONObject(j);
                        if (c != null && "output_text".equals(c.optString("type"))) {
                            if (text.length() > 0) text.append("\n");
                            text.append(c.optString("text"));
                        }
                    }
                }
                output = text.toString().trim();
                if (output.isEmpty()) throw new Exception("No text returned");
            } catch (Exception exception) {
                error = exception.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String resultFinal = output, errorFinal = error;
            MAIN.post(() -> callback.done(resultFinal, errorFinal));
        });
    }
}
