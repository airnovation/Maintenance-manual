package com.airnovation.aivoicekeyboard;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Offline pinyin -> selectable simplified Chinese candidates. No network during typing. */
public final class PinyinDictionary {
    public static final class Candidate {
        public final String text;
        public final int consumed;
        Candidate(String text, int consumed) {
            this.text = text;
            this.consumed = consumed;
        }
    }

    private final Map<String, List<String>> entries = new LinkedHashMap<>();

    public PinyinDictionary(Context context) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                context.getAssets().open("pinyin_candidates.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] parts = line.split("\\t", 2);
                if (parts.length != 2) continue;
                List<String> list = entries.get(parts[0]);
                if (list == null) {
                    list = new ArrayList<>();
                    entries.put(parts[0], list);
                }
                for (String hanzi : parts[1].split(",")) {
                    if (!hanzi.isEmpty() && !list.contains(hanzi)) list.add(hanzi);
                }
            }
        } catch (Exception ignored) {
            // The keyboard stays usable as a plain input method if the asset is unavailable.
        }
    }

    /** Longest known complete prefix first, with exact phrase candidates ranked above chars. */
    public List<Candidate> suggest(String typed, int limit) {
        if (typed == null) return Collections.emptyList();
        String key = typed.toLowerCase().replace("'", "").replace(" ", "");
        if (key.length() == 0) return Collections.emptyList();
        List<Candidate> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int size = key.length(); size > 0; size--) {
            List<String> hits = entries.get(key.substring(0, size));
            if (hits == null) continue;
            for (String text : hits) {
                if (seen.add(text)) result.add(new Candidate(text, size));
                if (result.size() >= limit) return result;
            }
            // Avoid swamping complete-word candidates with shorter homophones.
            if (result.size() >= 5) break;
        }
        return result;
    }

    public boolean isReady() { return !entries.isEmpty(); }
}
