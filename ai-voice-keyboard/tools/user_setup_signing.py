#!/usr/bin/env python3
"""USER-RUN ONLY. Creates one app signing identity and configures GitHub.
Never run this script through an assistant/tool. Run it yourself on a trusted
computer, or your own Cloud Shell after approving that storage choice.
Requires Python 3, a JDK providing keytool, and an already authenticated gh CLI.
No login, token generation, software installation, push or release is performed.
"""
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

REPO = 'airnovation/Maintenance-manual'
ALIAS = 'ai-voice-keyboard'
NAMES = ['AE_KEYSTORE_BASE64', 'AE_STORE_PASSWORD', 'AE_KEY_ALIAS', 'AE_KEY_PASSWORD']

def command(args, *, data=None, env=None):
    result = subprocess.run(args, input=data, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, env=env)
    if result.returncode:
        # Do not echo tool output; it might contain confidential diagnostic data.
        raise RuntimeError('Step failed: ' + args[0] + ' ' + args[1] + '. No secret values are shown.')
    return result.stdout


def main():
    if not sys.stdin.isatty():
        raise RuntimeError('Run interactively yourself; non-interactive execution is disabled.')
    for tool in ['gh', 'keytool']:
        if not shutil.which(tool):
            raise RuntimeError(tool + ' is missing. Install from the official source before continuing.')
    command(['gh', 'auth', 'status', '--hostname', 'github.com'])
    command(['gh', 'repo', 'view', REPO, '--json', 'nameWithOwner'])
    existing = json.loads(command(['gh', 'secret', 'list', '--repo', REPO, '--json', 'name']))
    if set(NAMES) & {row['name'] for row in existing}:
        raise RuntimeError('Signing secrets already exist. Stop and review the existing identity; never replace it blindly.')
    variables = json.loads(command(['gh', 'variable', 'list', '--repo', REPO, '--json', 'name']))
    if 'AE_SIGNING_CERT_SHA256' in {row['name'] for row in variables}:
        raise RuntimeError('Signing fingerprint already exists. Stop and review the existing identity.')
    root = Path.home() / 'ai-voice-keyboard-signing-private'
    if root.exists():
        raise RuntimeError('Signing directory already exists. Reuse/recover that identity; do not create another.')
    print('This creates ONE long-lived signing key for AI Voice Keyboard.')
    print('Only these signing credentials will be sent to GitHub Actions secrets in ' + REPO + '.')
    print('The public certificate fingerprint will be stored as a repository variable.')
    print('It cannot update older APKs signed with a different key. No APK is installed or published here.')
    if input('Type CREATE AND CONFIGURE to continue: ') != 'CREATE AND CONFIGURE':
        return
    password = getpass.getpass('Choose a strong signing password; save it in your password manager: ')
    if len(password) < 16 or password != getpass.getpass('Repeat signing password: '):
        raise RuntimeError('Passwords must match and have at least 16 characters.')
    os.umask(0o077)
    root.mkdir(mode=0o700)
    keystore = root / 'ai-voice-keyboard.p12'
    public_cert = root / 'certificate.der'
    env = os.environ.copy()
    env['AE_LOCAL_SIGNING_PASSWORD'] = password
    command(['keytool', '-genkeypair', '-noprompt', '-alias', ALIAS,
             '-keyalg', 'RSA', '-keysize', '3072', '-sigalg', 'SHA256withRSA',
             '-validity', '10000', '-storetype', 'PKCS12', '-keystore', str(keystore),
             '-storepass:env', 'AE_LOCAL_SIGNING_PASSWORD',
             '-keypass:env', 'AE_LOCAL_SIGNING_PASSWORD',
             '-dname', 'CN=AI Voice Keyboard'], env=env)
    command(['keytool', '-exportcert', '-alias', ALIAS, '-keystore', str(keystore),
             '-storepass:env', 'AE_LOCAL_SIGNING_PASSWORD', '-file', str(public_cert)], env=env)
    fingerprint = hashlib.sha256(public_cert.read_bytes()).hexdigest()
    print('Encrypted signing file created at: ' + str(keystore))
    print('Back up that file in a trusted encrypted vault and retain the password separately.')
    print('Cloud Shell is NOT a permanent backup. Never send this file or password in chat.')
    print('Public certificate SHA256: ' + fingerprint)
    if input('Type BACKED UP after securing your backup, to configure GitHub: ') != 'BACKED UP':
        print('Stopped. Key remains saved; no GitHub settings were changed. Do not rerun to create another key.')
        return
    values = [base64.b64encode(keystore.read_bytes()), password.encode(), ALIAS.encode(), password.encode()]
    for name, value in zip(NAMES, values):
        command(['gh', 'secret', 'set', name, '--repo', REPO], data=value)
        print('Configured ' + name)
    command(['gh', 'variable', 'set', 'AE_SIGNING_CERT_SHA256', '--repo', REPO], data=fingerprint.encode())
    print('Setup complete. Return ONLY the public fingerprint or “setup complete” to your assistant.')
    print('No build/release was triggered. Retain the signing file and password for future updates.')

if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, KeyboardInterrupt) as exc:
        print(str(exc) or 'Canceled.', file=sys.stderr)
        print('Stop and preserve any existing signing file. Partial configuration must be reviewed, not blindly retried.', file=sys.stderr)
        sys.exit(1)
