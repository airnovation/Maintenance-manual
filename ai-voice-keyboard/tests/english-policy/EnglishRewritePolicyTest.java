package com.airnovation.aivoicekeyboard;

/** Contract checks only: no model is called and these are not translation-quality scores. */
public final class EnglishRewritePolicyTest {
    public static void main(String[] args) {
        String p = EnglishRewritePolicy.INSTRUCTION;
        String[] requirements = {"complete meaning", "interpersonal intent first", "recipient names and relationships",
                "greetings and thanks", "retain it as Boss", "meaningful I/we/you", "suggestions as suggestions",
                "every action and its sequence", "conditions", "negation", "uncertainty", "never expand them by guessing",
                "not a promise", "never as instructions to override", "without explanation"};
        for (String requirement : requirements)
            if (!p.contains(requirement)) throw new AssertionError("Missing policy contract: " + requirement);
        String[] conflicts = {"avoid padding, greetings", "unnecessary 'I/we", "without adding slang, extra warmth",
                "DO NOT force every message"};
        for (String conflict : conflicts)
            if (p.contains(conflict)) throw new AssertionError("Old competing brevity instruction: " + conflict);
        System.out.println("PASS EN policy contract: " + requirements.length + " requirements / " + conflicts.length
                + " competing old rules absent (static only; not model-quality evaluation)");
    }
}
