import unittest
from unittest.mock import patch
import subprocess
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import verify_isolated_test as verify

BADGING = "package: name='com.airnovation.aivoicekeyboard.assistanttest' versionCode='16' versionName='0.16-gate-diagnostics-isolated-test'\napplication-label:'AI Voice Keyboard - Isolated Test'\n"
SIGNER = 'Verifies\nNumber of signers: 1\nSigner #1 certificate SHA-256 digest: ' + 'ab' * 32 + '\n'
COMMIT = '1234567890abcdef1234567890abcdef12345678'

class VerificationTests(unittest.TestCase):
    def validate(self, badging=BADGING, signer=SIGNER, commit=COMMIT, run='1234'):
        return verify.validate(badging, signer, commit, run)

    def test_valid(self):
        self.assertEqual(self.validate()['tag'], 'isolated-assistant-test-1234567890ab-1234')

    def test_production_package_rejected(self):
        with self.assertRaises(ValueError): self.validate(BADGING.replace('.assistanttest', ''))

    def test_wrong_version_code(self):
        with self.assertRaises(ValueError): self.validate(BADGING.replace("versionCode='16'", "versionCode='11'"))

    def test_wrong_version_name(self):
        with self.assertRaises(ValueError): self.validate(BADGING.replace('-isolated-test', ''))

    def test_wrong_label(self):
        with self.assertRaises(ValueError): self.validate(BADGING.replace(' - Isolated Test', ''))

    def test_debuggable(self):
        with self.assertRaises(ValueError): self.validate(BADGING + 'application-debuggable\n')

    def test_multiple_certificates(self):
        with self.assertRaises(ValueError): self.validate(signer=SIGNER + 'Signer #2 certificate SHA-256 digest: ' + 'cd' * 32 + '\n')

    def test_unverified_signature(self):
        with self.assertRaises(ValueError): self.validate(signer=SIGNER.replace('Verifies\n', ''))

    def test_malformed_cert(self):
        with self.assertRaises(ValueError): self.validate(signer=SIGNER.replace('ab' * 32, 'ab'))

    def test_injection_identity(self):
        for args in ({'commit': 'HEAD'}, {'run': '1\nfoo=bar'}):
            with self.assertRaises(ValueError): self.validate(**args)

    def test_localized_wrong_label(self):
        with self.assertRaises(ValueError): self.validate(BADGING + "application-label-zh:'Production Keyboard'\n")

    def test_tool_failure(self):
        with patch.object(verify.subprocess, 'run', return_value=subprocess.CompletedProcess([], 1, 'Verifies', '')):
            with self.assertRaises(RuntimeError): verify.checked(['apksigner'])

if __name__ == '__main__':
    unittest.main()
