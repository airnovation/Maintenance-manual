# Formal-package EN update: 0.21.0 (versionCode 21)

The user's latest direction is a formal-package build, not another independent test app. This validation stage therefore builds the existing `release` variant as **com.airnovation.aivoicekeyboard**, without an applicationId suffix. VersionCode 21 is above the verified source/release history through code 20. The EN fixes already validated in commit `4e4f8f9d23d792246694384b48df634b07bed02e` are unchanged.

`validate-en-review.yml` runs only on the dedicated validation branch, with read-only repository permissions. It compiles, lints and assembles the release variant, disables signing only for this CI invocation, and verifies the production package/version plus absence of signing data. The artifact is explicitly **unsigned and not installable**. No release is published, no phone is installed/uninstalled, and existing main/prototype branches are not changed. This document does not assert a pending CI run has passed; use the exact commit's Actions result.

## Signing and upgrade boundary

The original v0.10 build reported no configured stable-signing credentials and used the runner's Android Debug certificate. Its APK artifact contains public certificate SHA-256 `9b385fb449d84162d9faa45bceee5f4c5d4ccc246c333155ffd81cff92ff2422`. The original workflow did not archive the private key, and the user reports no backup. The installed phone identity remains unconfirmed.

A newly generated formal signing identity cannot update an installed APK signed with a different key. Preparing the same production package is not proof of upgrade compatibility. Secure custody/configuration of the new identity is a separate authorized setup process; never place its private key/password in source, logs, artifacts or chat. Do not run the repository's USER-RUN ONLY signing script through an assistant. Signing/release/installation must wait for their appropriate authorization and verified secure storage. Never uninstall the old keyboard automatically; the user must preserve settings before any separately approved reinstall.

## Verification commands

From `ai-voice-keyboard/`: `sh tests/run-tests.sh`, `python3 -m unittest discover -s tests -p 'test_*.py'`, and `python3 -m unittest discover -s tools -p 'test_*.py'`.

The validation workflow then runs `:app:compileReleaseJavaWithJavac :app:lintRelease :app:assembleRelease` with a temporary no-signing Gradle init script. Version-dependent isolated-variant metadata fixtures are kept aligned with the shared version; that legacy variant is not the artifact requested or built here.

Actual phone behavior and live model translation quality/latency are still unverified. Mic and manual sending behavior are unchanged.
