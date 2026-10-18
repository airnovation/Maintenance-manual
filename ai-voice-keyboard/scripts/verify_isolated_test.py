#!/usr/bin/env python3
"""Verify an isolated test APK. Reads APK/public certificate only, never keys."""
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess

PACKAGE = 'com.airnovation.aivoicekeyboard.assistanttest'
VERSION_CODE = '20'
VERSION_NAME = '0.20-compact-theme-isolated-test'
LABEL = 'AI Voice Keyboard - Isolated Test'

def validate(badging, signer, commit, run_id):
    package_lines = re.findall(r'^package: (.+)$', badging, re.M)
    if len(package_lines) != 1:
        raise ValueError('Expected exactly one APK package declaration')
    fields = dict(re.findall(r"([A-Za-z]+)='([^']*)'", package_lines[0]))
    if fields.get('name') != PACKAGE or fields.get('versionCode') != VERSION_CODE or fields.get('versionName') != VERSION_NAME:
        raise ValueError('Unexpected package/version')
    labels = re.findall(r"^application-label(?:-[^:]+)?:'([^']*)'$", badging, re.M)
    if not labels or any(label != LABEL for label in labels):
        raise ValueError('Unexpected test app label')
    if re.search(r'^application-debuggable(?:\s|$)', badging, re.M):
        raise ValueError('Debuggable APK rejected')
    certs = re.findall(r'^Signer #(\d+) certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$', signer, re.M)
    if len(certs) != 1 or certs[0][0] != '1':
        raise ValueError('Expected exactly one signing certificate')
    counts = re.findall(r'^Number of signers: (\d+)\s*$', signer, re.M)
    if counts != ['1'] or not re.search(r'^Verifies\s*$', signer, re.M):
        raise ValueError('APK signature verification not confirmed')
    if not re.fullmatch(r'[0-9a-f]{40}', commit) or not re.fullmatch(r'[1-9][0-9]*', run_id):
        raise ValueError('Invalid immutable build identity')
    return {'package': PACKAGE, 'versionCode': VERSION_CODE, 'versionName': VERSION_NAME,
            'label': LABEL, 'certificate_sha256': certs[0][1].lower(),
            'commit': commit, 'run_id': run_id,
            'tag': 'isolated-assistant-test-' + commit[:12] + '-' + run_id}

def checked(args):
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if result.returncode:
        raise RuntimeError('APK tool verification failed: ' + Path(args[0]).name)
    return result.stdout

def main():
    apk = Path('app/build/outputs/apk/isolatedTest/app-isolatedTest.apk')
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk:
        raise ValueError('Android SDK location missing')
    build_tools = Path(sdk) / 'build-tools' / '35.0.0'
    badging = checked([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)])
    signer = checked([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(apk)])
    metadata = validate(badging, signer, os.environ['GITHUB_SHA'], os.environ['GITHUB_RUN_ID'])
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    # Fail closed on an existing tag, including an empty remote response error.
    remote_tags = checked(['git', 'ls-remote', '--tags', 'origin', 'refs/tags/' + metadata['tag']])
    if remote_tags.strip():
        raise ValueError('Release tag already exists; refusing overwrite')
    output = Path('isolated-test')
    output.mkdir(exist_ok=False)
    copied = output / 'AI-Voice-Keyboard-Isolated-Test.apk'
    shutil.copyfile(apk, copied)
    if hashlib.sha256(copied.read_bytes()).hexdigest() != digest:
        raise ValueError('APK copy hash mismatch')
    metadata['apk_sha256'] = digest
    (output / (copied.name + '.sha256')).write_text(digest + '  ' + copied.name + '\n')
    text = 'ISOLATED TEST ONLY. This build uses a one-time development certificate, NOT the production signing identity.\n'
    text += 'Installs separately from the existing keyboard. Future test builds may not update this app in place.\n'
    text += ''.join(key + '=' + value + '\n' for key, value in metadata.items())
    (output / 'metadata.txt').write_text(text)
    with open(os.environ['GITHUB_OUTPUT'], 'a') as stream:
        stream.write('tag=' + metadata['tag'] + '\n')
    print(text, end='')

if __name__ == '__main__':
    main()
