package com.airnovation.aivoicekeyboard;

/** Per-panel, RAM-only voice drafting. No editor, accessibility, history or network access. */
public final class VoiceDraftSession {
    public static final int MAX_TRANSCRIPT_CHARS = 3000;
    public static final int MAX_DRAFT_CHARS = 4000;
    private long generation;
    private boolean active, revising;
    private String transcript = "", draft = "", revisionDraft = "";

    /** Only an explicit revision retains the previous accepted draft. */
    public synchronized long start(boolean revise) {
        if (revise && draft.isEmpty()) throw new IllegalStateException("No draft to revise");
        generation++;
        active = true;
        revising = revise;
        revisionDraft = revise ? draft : "";
        transcript = "";
        if (!revise) draft = "";
        return generation;
    }

    public synchronized boolean active(long token) { return active && token == generation; }

    public synchronized void invalidate() {
        generation++;
        active = false;
        revising = false;
        transcript = "";
        draft = "";
        revisionDraft = "";
    }

    public synchronized String draft() { return draft; }
    public synchronized String transcript() { return transcript; }

    /** Null leaves that field unchanged, for example retaining a transcript after a draft error. */
    public synchronized boolean accept(long token, String spoken, String proposed) {
        if (!active(token) || (spoken == null && proposed == null)
                || (spoken != null && (spoken.trim().isEmpty() || spoken.length() > MAX_TRANSCRIPT_CHARS))
                || (proposed != null && (proposed.trim().isEmpty() || proposed.length() > MAX_DRAFT_CHARS))) return false;
        if (spoken != null) transcript = spoken;
        if (proposed != null) draft = proposed;
        return true;
    }

    /** A caller cannot attach old text to a fresh request or substitute a different revision base. */
    synchronized boolean permitsPreviousDraft(long token, String previousDraft) {
        if (!active(token)) return false;
        if (revising) return !revisionDraft.isEmpty() && revisionDraft.equals(previousDraft);
        return previousDraft == null || previousDraft.isEmpty();
    }
}
