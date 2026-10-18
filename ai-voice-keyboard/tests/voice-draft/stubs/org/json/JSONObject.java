package org.json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Host-only fixture DOM. The String constructor recognizes explicitly registered
 * opaque response bodies; it does NOT parse JSON. These tests exercise transport
 * and response-policy control flow, never JSON serialization/format correctness.
 */
public final class JSONObject {
    private static final Map<String, JSONObject> FIXTURES = new ConcurrentHashMap<>();
    public static int serializations, constructions;
    private final Map<String, Object> values = new LinkedHashMap<>();
    public JSONObject() { constructions++; }
    public JSONObject(String source) {
        constructions++;
        JSONObject fixture = FIXTURES.get(source);
        if (fixture == null) throw new IllegalArgumentException("Unregistered fixture body");
        values.putAll(fixture.values);
    }
    public static void registerFixture(String body, JSONObject parsedFixture) {
        FIXTURES.put(body, parsedFixture);
    }
    public static void clearFixtures() { FIXTURES.clear(); }
    public JSONObject put(String key, Object value) { values.put(key, value); return this; }
    public Object opt(String key) { return values.get(key); }
    public int length() { return values.size(); }
    public JSONArray optJSONArray(String key) {
        Object value = values.get(key);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }
    public JSONObject optJSONObject(String key) {
        Object value = values.get(key);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }
    public String optString(String key, String fallback) {
        Object value = values.get(key);
        return value instanceof String ? (String) value : fallback;
    }
    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
    @Override public String toString() { serializations++; return values.toString(); }
}
