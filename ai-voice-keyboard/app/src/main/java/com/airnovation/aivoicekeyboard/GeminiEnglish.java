package com.airnovation.aivoicekeyboard;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

/** REST-based message editing. No credential is embedded into the application binary. */
public final class GeminiEnglish {
    private static final ExecutorService EXECUTOR = EnglishRequest.newExecutor();
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

    public static EnglishRequest rewrite(String apiKey, String original, Callback callback) {
        EnglishRequest request = new EnglishRequest(EnglishRequest.Provider.GEMINI);
        request.execute(EXECUTOR, () -> {
            String output = null;
            String error = null;
            try {
                request.check();
                ApiKeyValidation.requireValid(apiKey);
                byte[] payload = buildRequest(original).toString().getBytes(StandardCharsets.UTF_8);
                for (String model : MODELS) {
                    request.check();
                    HttpURLConnection conn = null;
                    try {
                        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                                + model + ":generateContent");
                        conn = (HttpURLConnection) url.openConnection();
                        request.attach(conn);
                        conn.setInstanceFollowRedirects(false);
                        conn.setRequestMethod("POST");
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(40000);
                        conn.setDoOutput(true);
                        conn.setRequestProperty("x-goog-api-key", apiKey);
                        conn.setRequestProperty("Content-Type", "application/json");
                        request.check();
                        try (OutputStream stream = conn.getOutputStream()) { request.check(); stream.write(payload); }
                        request.check();

                        int status = conn.getResponseCode();
                        request.check();
                        if (status == 404) continue;
                        if (status < 200 || status >= 300) throw SafeApiErrors.http(status);
                        String response = SafeApiErrors.readResponse(conn.getInputStream());
                        request.check();
                        output = parseText(response);
                        if (output.isEmpty()) {
                            throw SafeApiErrors.failure(SafeApiErrors.Kind.EMPTY_RESPONSE);
                        }
                        break;
                    } finally {
                        request.detach(conn);
                    }
                }
                if (output == null) {
                    throw SafeApiErrors.failure(SafeApiErrors.Kind.MODELS_UNAVAILABLE);
                }
            } catch (EnglishRequest.Cancelled ignored) { return; }
            catch (Exception e) {
                // Do not log the key, prompt, response body or any confidential message.
                output = null;
                error = SafeApiErrors.message(e);
            }
            final String resultFinal = output, errorFinal = error;
            request.finishWork(error == null ? EnglishRequest.Outcome.SUCCESS : EnglishRequest.Outcome.FAILED);
            MAIN.post(() -> request.deliver(() -> callback.done(resultFinal, errorFinal)));
        }, () -> MAIN.post(() -> request.deliver(() -> callback.done(null, EnglishRequest.BUSY_ERROR))));
        return request;
    }

    private static JSONObject buildRequest(String original) throws Exception {
        String instruction = EnglishRewritePolicy.INSTRUCTION;
        JSONObject body = new JSONObject();
        body.put("systemInstruction", new JSONObject().put("parts",
                new JSONArray().put(new JSONObject().put("text", instruction))));
        JSONObject userContent = new JSONObject().put("role", "user").put("parts",
                new JSONArray().put(new JSONObject().put("text", original)));
        body.put("contents", new JSONArray().put(userContent));
        body.put("generationConfig", new JSONObject().put("maxOutputTokens", 1024));
        return body;
    }

    private static String parseText(String response) throws Exception {
        JSONObject result = new JSONObject(response);
        JSONArray candidates = result.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw SafeApiErrors.failure(SafeApiErrors.Kind.INVALID_RESPONSE);
        }
        JSONObject candidate = candidates.optJSONObject(0);
        if (candidate == null || !"STOP".equals(candidate.optString("finishReason", "")))
            throw SafeApiErrors.failure(SafeApiErrors.Kind.INVALID_RESPONSE);
        JSONObject content = candidate.optJSONObject("content");
        JSONArray parts = content == null ? null : content.optJSONArray("parts");
        StringBuilder text = new StringBuilder();
        if (parts != null) for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part != null && !part.optBoolean("thought", false)) text.append(part.optString("text", ""));
        }
        return text.toString().trim();
    }
}
