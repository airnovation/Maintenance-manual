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

public final class OpenAiEnglish {
    private static final ExecutorService EXECUTOR = EnglishRequest.newExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Callback { void done(String result, String error); }
    private OpenAiEnglish() {}

    public static EnglishRequest rewrite(String apiKey, String original, Callback callback) {
        EnglishRequest request = new EnglishRequest(EnglishRequest.Provider.OPENAI);
        request.execute(EXECUTOR, () -> {
            String output = null;
            String error = null;
            HttpURLConnection connection = null;
            try {
                request.check();
                ApiKeyValidation.requireValid(apiKey);
                connection = (HttpURLConnection) new URL("https://api.openai.com/v1/responses").openConnection();
                request.attach(connection);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(20000);
                connection.setReadTimeout(40000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Content-Type", "application/json");
                JSONObject body = new JSONObject();
                body.put("model", "gpt-4.1-mini");
                body.put("store", false);
                body.put("instructions", EnglishRewritePolicy.INSTRUCTION);
                body.put("input", original);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                request.check();
                try (OutputStream stream = connection.getOutputStream()) { request.check(); stream.write(bytes); }
                request.check();
                int status = connection.getResponseCode();
                request.check();
                if (status < 200 || status >= 300) throw SafeApiErrors.http(status);
                JSONObject json = new JSONObject(SafeApiErrors.readResponse(connection.getInputStream()));
                request.check();
                if (!"completed".equals(json.optString("status", "")))
                    throw SafeApiErrors.failure(SafeApiErrors.Kind.INVALID_RESPONSE);
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
                if (output.isEmpty()) throw SafeApiErrors.failure(SafeApiErrors.Kind.EMPTY_RESPONSE);
            } catch (EnglishRequest.Cancelled ignored) { return; }
            catch (Exception exception) {
                output = null;
                error = SafeApiErrors.message(exception);
            } finally {
                request.detach(connection);
            }
            final String resultFinal = output, errorFinal = error;
            request.finishWork(error == null ? EnglishRequest.Outcome.SUCCESS : EnglishRequest.Outcome.FAILED);
            MAIN.post(() -> request.deliver(() -> callback.done(resultFinal, errorFinal)));
        }, () -> MAIN.post(() -> request.deliver(() -> callback.done(null, EnglishRequest.BUSY_ERROR))));
        return request;
    }
}
