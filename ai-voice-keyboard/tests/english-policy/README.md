# Meaning-first EN policy (offline checks)

Run `sh tests/english-policy/run-tests.sh` from the project directory.

These tests ensure both providers use the same policy and keep the explicit meaning-preservation contract. They **do not prove a model will obey it**. `fixtures.json` contains synthetic Chinese/mixed-language input, illustrative human-authored English references and meaning/tone criteria. References are not exact-match gold strings: other natural phrasings may be equally good.

For any separately authorized live evaluation, review the actual output against each criterion: address and recipient relation, suggestion versus command/commitment, every step/condition, greeting/thanks already present, negation/uncertainty, names/numbers/terms, and no invented warmth or promises. A fluent sentence missing “Boss” or changing “we should” to a command fails. Removing speech fillers is allowed. Do not upload private real chat text or use credentials in these offline tests. Test quality and latency separately; log timings without message text.
