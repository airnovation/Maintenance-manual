package com.airnovation.aivoicekeyboard;

import java.util.Locale;

/** Local input hygiene only; this cannot establish whether a provider accepts a key. */
public final class ApiKeyValidation {
    // Broad transport bounds, not a provider prefix/format or an authentication check.
    static final int MIN_LENGTH = 16, MAX_LENGTH = 4096;
    private ApiKeyValidation() { }

    /** Null means locally acceptable. Never returns any portion of the supplied value. */
    public static String validationMessage(String value) {
        if (value == null || value.isEmpty())
            return "Paste and save your provider API key in Settings first.";
        if (value.length() < MIN_LENGTH || value.length() > MAX_LENGTH) return invalid();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            // No whitespace, CR/LF, controls, DEL, Unicode separators or non-ASCII headers.
            if (c < 0x21 || c > 0x7e) return invalid();
        }
        if (value.charAt(0) == '"' || value.charAt(value.length() - 1) == '"'
                || value.charAt(0) == '\'' || value.charAt(value.length() - 1) == '\''
                || value.charAt(0) == '`' || value.charAt(value.length() - 1) == '`') return invalid();
        String lower = value.toLowerCase(Locale.ROOT);
        // Catch recognizable copied URLs, header lines and diagnostics, without requiring
        // a provider-specific prefix, length or restricted alphanumeric alphabet.
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("authorization:") || lower.startsWith("x-goog-api-key:")
                || lower.startsWith("java.lang.") || lower.startsWith("java.io.")
                || lower.startsWith("unexpectedchar0x") || lower.startsWith("unexpectedcharacter")
                || lower.startsWith("illegalargumentexception:")
                || lower.startsWith("{\"error\"") || lower.startsWith("{\"message\"")) return invalid();
        return null;
    }

    public static void requireValid(String value) throws SafeApiErrors.Failure {
        String message = validationMessage(value);
        if (message != null) throw SafeApiErrors.invalidKey(value == null || value.isEmpty());
    }

    private static String invalid() {
        return "API key format looks wrong. Paste only the key, without spaces, line breaks, quotes or error text.";
    }
}
