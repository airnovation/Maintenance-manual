# Local capture diagnostics (v0.13)

This adds a separate **本机诊断（不读文字）** button. It needs an enabled
keyboard and connected Accessibility service in a supported chat input. It does
not need a Gemini API key. It does not read node text, call a provider, record
microphone audio, insert a draft, or send a message. It ends the temporary
session immediately and displays only fixed labels and integer counters.

Only share the diagnostic-number region of the keyboard if asking for help;
there is no need to include the conversation above it. No automatic diagnostic
upload, raw node dump, log or persistent diagnostic file is created.

## Counts

- N: traversed nodes, including early returns
- M: recognized `message_text` candidates reaching geometry checks
- L: candidates without recognized list ancestry
- I/U: metadata-stage nodes with nonempty/missing resource IDs
- K: recognized list container nodes
- C: candidates not fully contained by ancestor clipping
- W: candidates overlapping another window
- S: candidates with unequal local/screen dimensions
- F: candidates reaching final same-window checks
- O: candidates rejected by same-window masks
- E: eligible candidates, without reading their text

Counts can overlap. N does not equal I+U because some nodes return earlier.
M=0 does not prove an alternative caption ID; inaccessible nodes or skipped
subtrees are also possible. K=0 with M>0 suggests list ancestry incompatibility.
C/W/S/O locate a rejection stage, not permission to disable that check.

The ordinary assistant still requires explicit consent and uses the existing
Gemini configuration. Its empty-result message may include a T counter for null
text at the existing permitted text-read stage. The separate diagnostic entry
never reaches that getter, and does not report T.

## What this does not change

Only package-matched `message_text` nodes are accepted. Package/session identity,
focused message editor, password exclusions, recognized list ancestry, complete
ancestor containment, original node dimensions and both external/same-window
occlusion checks remain. No caption ID has been guessed or added. No OCR,
content-description fallback, generic TextView scraping, history traversal or
image upload is introduced.

A partly clipped media parent does not by itself reject a fully visible child.
Synthetic geometry tests demonstrate this rule, but do not establish the real
WhatsApp Business node hierarchy. The user's observed empty-result error can
have multiple causes; this build collects content-free evidence before a
compatibility change is selected. It is not a claim that extraction is repaired.

## Validation

Pure-Java counters, synthetic clipping/filter cases, actual AssistantPanel with
fake Android boundaries, and actual VisibleMessageService against deterministic
synthetic Android nodes are tested offline. The metadata service tests make
message getText throw if called, including when a candidate would otherwise be
eligible. Android compilation/lint run in CI before isolated release. Host fakes
are not real-device layout, rendering or permission tests.

The development signing key is ephemeral. This APK cannot replace a previous
independent test APK signed with a different key; only that isolated test app
may need manual removal. Keep the original keyboard. No phone is modified
or uninstalled automatically.
