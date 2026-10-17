package com.airnovation.aivoicekeyboard;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Transcribe a completed, user-controlled recording.
 * The recording is sent only after the user taps Stop. No background listening.
 * Caller must delete the private temporary file after completion.
 */
public final class AudioTranscriber {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String[] GEMINI_MODELS = {
            "gemini-3.5-flash-lite", "gemini-3.8-flash", "gemini-3.6-flash"
    };
    public interface Callback { void done(String transcript, String error); }
    private AudioTranscriber() { }

    public static void transcribe(String provider, String apiKey, File audio, Callback callback) {
        IO.execute(() -> {
            String text = null, error = null;
            try {
                if (audio == null || !audio.isFile() || audio.length() < 100) {
                    throw new Exception("No recording. Please tap Mic, speak, then tap Stop.");
                }
                if (apiKey == null || apiKey.trim().isEmpty()) {
                    throw new Exception("Select and save your Gemini or OpenAI API key first.");
                }
                if (audio.length() > 12 * 1024 * 1024) {
                    throw new Exception("Recording is too large; try a shorter message.");
                }
                text = "gemini".equals(provider)
                        ? transcribeGemini(apiKey, audio)
                        : transcribeOpenAi(apiKey, audio);
                if (text == null || text.trim().isEmpty()) {
                    throw new Exception("No speech recognised. Try again.");
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                if (audio != null && audio.exists()) {
                    // A message never becomes a persisted recording in the app.
                    audio.delete();
                }
            }
            final String transcript = text == null ? null : text.trim();
            final String finalError = error;
            MAIN.post(() -> callback.done(transcript, finalError));
        });
    }

    private static String transcribeGemini(String key, File audio) throws Exception {
        byte[] bytes = readFile(audio);
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        JSONObject data = new JSONObject()
                .put("mime_type", "audio/m4a")
                .put("data", encoded);
        JSONArray parts = new JSONArray()
                .put(new JSONObject().put("text",
                        "Transcribe this recording verbatim in its original spoken languages, " +
                        "including Mandarin Chinese, English, Malay, and mixed technical words. " +
                        "Preserve names, units, and engineering terms. Output ONLY the transcript " +
                        "in the speaker's original languages, with natural punctuation. " +
                        "Do not translate or invent any content."))
                .put(new JSONObject().put("inline_data", data));
        JSONObject body = new JSONObject().put("contents", new JSONArray().put(
                new JSONObject().put("role", "user").put("parts", parts)));
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
        for (String model : GEMINI_MODELS) {
            HttpURLConnection connection = null;
            try {
                URL endpoint = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                        + model + ":generateContent");
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(65000);
                connection.setRequestProperty("x-goog-api-key", key);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream output = connection.getOutputStream()) { output.write(payload); }
                int code = connection.getResponseCode();
                String response = readStream(code < 300 ? connection.getInputStream()
                        : connection.getErrorStream());
                if (code == 404) continue;
                if (code < 200 || code >= 300) {
                    throw new Exception("Gemini transcription HTTP " + code + " (" +
                            coarseError(response) + ").");
                }
                JSONObject result = new JSONObject(response);
                JSONArray candidates = result.optJSONArray("candidates");
                if (candidates == null || candidates.length() == 0)
                    throw new Exception("Gemini returned no transcription.");
                JSONObject candidate = candidates.optJSONObject(0);
                JSONObject outputContent = candidate == null ? null : candidate.optJSONObject("content");
                JSONArray resultParts = outputContent == null ? null : outputContent.optJSONArray("parts");
                StringBuilder text = new StringBuilder();
                if (resultParts != null) for (int i = 0; i < resultParts.length(); i++) {
                    JSONObject item = resultParts.optJSONObject(i);
                    if (item != null) text.append(item.optString("text", ""));
                }
                return text.toString().trim();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }
        throw new Exception("Gemini transcription unavailable (HTTP 404 on available models).");
    }

    private static String transcribeOpenAi(String key, File audio) throws Exception {
        HttpURLConnection connection = null;
        String boundary = "VoiceKeyboardAudioForm2026";
        try {
            connection = (HttpURLConnection) new URL("https://api.openai.com/v1/audio/transcriptions")
                    .openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(65000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Authorization", "Bearer " + key);
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            try (OutputStream output = connection.getOutputStream()) {
                byte[] audioData = readFile(audio);
                write(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\ngpt-4o-mini-transcribe\r\n");
                write(output, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n");
                output.write(audioData);
                write(output, "\r\n--" + boundary + "--\r\n");
            }
            int status = connection.getResponseCode();
            String response = readStream(status < 300 ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300)
                throw new Exception("OpenAI transcription HTTP " + status + " (" +
                        coarseError(response) + ").");
            return new JSONObject(response).optString("text", "").trim();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void write(OutputStream out, String text) throws Exception {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readFile(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[16384];
            int n;
            while ((n = in.read(chunk)) != -1) out.write(chunk, 0, n);
            return out.toByteArray();
        }
    }

    private static String readStream(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
            return result.toString();
        }
    }

    private static String coarseError(String response) {
        // Never display raw provider error text: it may contain credential-like data.
        try {
            JSONObject err = new JSONObject(response).optJSONObject("error");
            String kind = err == null ? "" : err.optString("status", err.optString("type", ""));
            if (kind.matches("[A-Za-z_]{2,48}")) return kind;
        } catch (Exception ignored) { }
        return "UNKNOWN";
    }
}
