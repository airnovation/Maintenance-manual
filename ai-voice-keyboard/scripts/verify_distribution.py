#!/usr/bin/env python3
"""Read public APK metadata only; fail closed before distribution."""
import os
import re
import subprocess
from pathlib import Path


def run(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def source_version(text):
    code = re.search(r"\bversionCode\s+(\d+)\b", text)
    name = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", text)
    if not code or not name:
        raise ValueError("Missing literal source version")
    return int(code[1]), name[1]


def verify(source, badging, signing, expected, prior_codes):
    code, name = source_version(source)
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+)*(?:-[A-Za-z0-9.-]+)?", name):
        raise ValueError("Unsafe version name")
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("com.airnovation.aivoicekeyboard", str(code), name):
        raise ValueError("APK package/version differs from expected source")
    expected = expected.replace(":", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Expected public certificate SHA256 must be configured")
    certs = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", signing, re.M)
    if len(certs) != 1 or certs[0].lower() != expected:
        raise ValueError("APK signing certificate does not match approved identity")
    if prior_codes and code <= max(prior_codes):
        raise ValueError("versionCode must exceed every previously tagged versionCode")
    return code, name, expected


def main():
    source_path = "ai-voice-keyboard/app/build.gradle"
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools" / "35.0.0"
    apk = "ai-voice-keyboard/app/build/outputs/apk/debug/app-debug.apk"
    # apksigner verifies signatures, not just certificate extraction.
    signing = run(str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", apk)
    badging = run(str(sdk / "aapt"), "dump", "badging", apk)
    prior_codes = []
    for tag in run("git", "tag", "--list", "ai-voice-keyboard-*").splitlines():
        old = run("git", "show", f"{tag}:{source_path}")
        prior_codes.append(source_version(old)[0])
    code, name, fingerprint = verify(Path(source_path).read_text(), badging, signing,
        os.environ.get("EXPECTED_SIGNING_CERT_SHA256", ""), prior_codes)
    tag = f"ai-voice-keyboard-v{name}"
    if run("git", "tag", "--list", tag).strip():
        raise ValueError("Version tag already exists; never overwrite published builds")
    # Public metadata only. No credentials, message content or user settings.
    Path("distribution-metadata.txt").write_text(
        f"applicationId=com.airnovation.aivoicekeyboard\nversionCode={code}\nversionName={name}\n"
        f"certificateSha256={fingerprint}\ncommit={os.environ['GITHUB_SHA']}\n")
    with open(os.environ["GITHUB_OUTPUT"], "a") as output:
        output.write(f"tag={tag}\nversion={name}\n")


if __name__ == "__main__":
    main()
