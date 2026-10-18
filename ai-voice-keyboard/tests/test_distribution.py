import importlib.util, unittest
from pathlib import Path
p=Path(__file__).resolve().parents[1]/'scripts/verify_distribution.py'
s=importlib.util.spec_from_file_location('verifier',p);v=importlib.util.module_from_spec(s);s.loader.exec_module(v)
class VerificationTests(unittest.TestCase):
 def setUp(self):
  self.args=["versionCode 11\nversionName '0.11-assistant-prototype'", "package: name='com.airnovation.aivoicekeyboard' versionCode='11' versionName='0.11-assistant-prototype'",'Signer #1 certificate SHA-256 digest: '+'a'*64,'a'*64,[9,10]]
 def test_success(self):self.assertEqual(v.verify(*self.args)[:2],(11,'0.11-assistant-prototype'))
 def test_missing_fingerprint(self):
  self.args[3]=''
  with self.assertRaises(ValueError):v.verify(*self.args)
 def test_changed_signer(self):
  self.args[3]='b'*64
  with self.assertRaises(ValueError):v.verify(*self.args)
 def test_equal_or_lower_code(self):
  for n in [11,12]:
   self.args[4]=[n]
   with self.assertRaises(ValueError):v.verify(*self.args)
 def test_wrong_package(self):
  self.args[1]=self.args[1].replace('com.airnovation.aivoicekeyboard','other.package')
  with self.assertRaises(ValueError):v.verify(*self.args)
 def test_mislabeled_version(self):
  self.args[1]=self.args[1].replace('0.11-assistant-prototype','0.10-prototype')
  with self.assertRaises(ValueError):v.verify(*self.args)
 def test_multiple_signers(self):
  self.args[2]+='\nSigner #2 certificate SHA-256 digest: '+'a'*64
  with self.assertRaises(ValueError):v.verify(*self.args)
if __name__=='__main__':unittest.main()
