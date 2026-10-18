# Offline API-key and legacy transport safety regression

Run `sh tests/api-safety/run-tests.sh` from the project root. No credentials,
sockets, provider calls, Android device, microphone or actual keystore are used.

The suite compiles production ApiKeyValidation, SafeApiErrors, GeminiEnglish,
OpenAiEnglish and AudioTranscriber. Existing host Android stubs and fixture JSON
DOMs are reused from the voice-draft suite. The JSON DOMs are not real parsers;
these tests cover routing, cleanup and sanitization, not wire serialization or
live provider compatibility. Every URL connection is intercepted in memory.

The suite passes 121 cases and 1,694 checks. Checks include printable provider-agnostic keys, broad length limits, CR/LF and
all other ASCII controls/whitespace, Unicode/non-ASCII, copied URL/diagnostic/header
text, null/empty input, malformed keys producing zero HTTP requests, synthetic
exceptions that contain fake keys/private content, safe numeric HTTP statuses,
no error-body reads, no redirect following, 404-only Gemini model fallback,
bounded responses, incomplete responses, audio cleanup, hidden-thought exclusion,
and unchanged explicit-input EN instructions on successful requests.

Local validation only checks input hygiene. It does not prove API-key ownership,
validity, billing, quota, provider authorization or live-model availability.
