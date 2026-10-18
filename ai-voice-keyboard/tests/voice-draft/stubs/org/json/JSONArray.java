package org.json;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Minimal fixture DOM, not a JSON parser. */
public final class JSONArray {
    private final List<Object> values = new ArrayList<>();
    public JSONArray() { }
    public JSONArray(Collection<?> source) { values.addAll(source); }
    public JSONArray put(Object value) { values.add(value); return this; }
    public int length() { return values.size(); }
    public JSONObject getJSONObject(int index) { return (JSONObject) values.get(index); }
    public JSONObject optJSONObject(int index) {
        if (index < 0 || index >= values.size()) return null;
        Object value = values.get(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }
    @Override public String toString() { return values.toString(); }
}
