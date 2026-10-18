package com.airnovation.aivoicekeyboard;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLException;

/** Only fixed, app-owned messages or numeric HTTP statuses may leave a transport. */
public final class SafeApiErrors {
    static final int MAX_RESPONSE_BYTES = 65536;
    private SafeApiErrors() { }

    enum Kind { INVALID_RESPONSE, EMPTY_RESPONSE, RESPONSE_TOO_LARGE, NO_RECORDING,
        AUDIO_TOO_LARGE, EMPTY_TRANSCRIPT, MODELS_UNAVAILABLE }

    /** Construction is private: a provider body or raw exception cannot become a safe error. */
    public static final class Failure extends Exception {
        private Failure(String fixedMessage) { super(fixedMessage); }
    }

    static Failure invalidKey(boolean missing) {
        return new Failure(ApiKeyValidation.validationMessage(missing ? "" : "invalid"));
    }

    static Failure failure(Kind kind) {
        switch (kind) {
            case EMPTY_RESPONSE: return new Failure("The AI returned no text. Please try again.");
            case RESPONSE_TOO_LARGE: return new Failure("The AI response was too large. Try a shorter message.");
            case NO_RECORDING: return new Failure("No recording. Tap Mic, speak, then tap Stop.");
            case AUDIO_TOO_LARGE: return new Failure("Recording is too large. Try a shorter message.");
            case EMPTY_TRANSCRIPT: return new Failure("No speech recognised. Please try again.");
            case MODELS_UNAVAILABLE: return new Failure("The configured AI models are unavailable. Check provider access in Settings.");
            default: return new Failure("The AI response was incomplete or invalid. Please try again.");
        }
    }

    static Failure http(int status) {
        String code = status >= 100 && status <= 599 ? " (HTTP " + status + ")" : "";
        if (status == 401 || status == 403)
            return new Failure("API access was refused" + code + ". Check the key and provider permissions in Settings.");
        if (status == 429)
            return new Failure("API quota or rate limit reached" + code + ". Check billing/quota or try later.");
        return new Failure("The AI request failed" + code + ". Check provider access or try again.");
    }

    /** Never return getMessage()/toString()/class name from an arbitrary exception. */
    public static String message(Throwable failure) {
        if (failure instanceof Failure) return failure.getMessage();
        if (failure instanceof SocketTimeoutException)
            return "The AI request timed out. Please try again.";
        if (failure instanceof SSLException)
            return "A secure connection could not be established. Check your network and device time.";
        return "The AI request could not be completed. Check your network or try again.";
    }

    static String readResponse(InputStream input) throws Exception {
        if (input == null) throw failure(Kind.INVALID_RESPONSE);
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = in.read(buffer)) != -1) {
                if (out.size() + count > MAX_RESPONSE_BYTES) throw failure(Kind.RESPONSE_TOO_LARGE);
                out.write(buffer, 0, count);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
