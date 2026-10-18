"""All commands mocked; no real keys, login, network or settings changes."""
import contextlib, importlib.util, io, json, subprocess, tempfile, unittest
from pathlib import Path
from unittest.mock import patch
spec=importlib.util.spec_from_file_location('setup',Path(__file__).with_name('user_setup_signing.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
PASSWORD='MOCK_ONLY_NOT_A_REAL_PASSWORD'
class SetupTests(unittest.TestCase):
 def setUp(self):
  self.temp=tempfile.TemporaryDirectory();self.home=Path(self.temp.name);self.calls=[]
  self.secrets=[];self.variables=[];self.fail=None
 def tearDown(self):self.temp.cleanup()
 def fake_command(self,args,*,data=None,env=None):
  self.calls.append((args,data,env))
  if self.fail and args[:3]==self.fail:raise RuntimeError('Simulated failure')
  if args[:3]==['gh','secret','list']:return json.dumps(self.secrets).encode()
  if args[:3]==['gh','variable','list']:return json.dumps(self.variables).encode()
  if args[:2]==['keytool','-genkeypair']:Path(args[args.index('-keystore')+1]).write_bytes(b'MOCK_KEYSTORE_BYTES')
  if args[:2]==['keytool','-exportcert']:Path(args[args.index('-file')+1]).write_bytes(b'MOCK_PUBLIC_CERTIFICATE')
  return b''
 def execute(self,answers=('CREATE AND CONFIGURE','BACKED UP'),passwords=(PASSWORD,PASSWORD)):
  out=io.StringIO()
  with patch.object(m.sys.stdin,'isatty',return_value=True),patch.object(m.shutil,'which',return_value='/mock/bin'),patch.object(m.Path,'home',return_value=self.home),patch.object(m,'command',side_effect=self.fake_command),patch('builtins.input',side_effect=answers),patch.object(m.getpass,'getpass',side_effect=passwords),contextlib.redirect_stdout(out):m.main()
  return out.getvalue()
 def test_complete_flow_no_secret_output_or_argv(self):
  output=self.execute();self.assertNotIn(PASSWORD,output);self.assertNotIn('MOCK_KEYSTORE_BYTES',output)
  writes=[c for c in self.calls if c[0][:3]==['gh','secret','set']]
  self.assertEqual([c[0][3] for c in writes],m.NAMES)
  for args,data,env in self.calls:
   self.assertNotIn(PASSWORD,' '.join(args))
   if args[0]=='gh':self.assertIsNone(env)
  self.assertEqual(len([c for c in self.calls if c[0][:3]==['gh','variable','set']]),1)
 def test_existing_secret_never_overwritten(self):
  self.secrets=[{'name':'AE_KEY_ALIAS'}]
  with self.assertRaises(RuntimeError):self.execute()
  self.assertFalse((self.home/'ai-voice-keyboard-signing-private').exists())
 def test_existing_variable_blocks(self):
  self.variables=[{'name':'AE_SIGNING_CERT_SHA256'}]
  with self.assertRaises(RuntimeError):self.execute()
 def test_existing_key_directory_blocks(self):
  (self.home/'ai-voice-keyboard-signing-private').mkdir()
  with self.assertRaises(RuntimeError):self.execute()
  self.assertFalse(any(c[0][0]=='keytool' for c in self.calls))
 def test_no_backup_confirmation_no_upload(self):
  self.execute(('CREATE AND CONFIGURE','NOT READY'))
  self.assertFalse(any(c[0][:3]==['gh','secret','set'] for c in self.calls))
 def test_cancel_before_creation(self):
  self.execute(('CANCEL',));self.assertFalse((self.home/'ai-voice-keyboard-signing-private').exists())
 def test_password_mismatch_no_creation(self):
  with self.assertRaises(RuntimeError):self.execute(passwords=(PASSWORD,'different'))
  self.assertFalse((self.home/'ai-voice-keyboard-signing-private').exists())
 def test_partial_failure_stops(self):
  self.fail=['gh','secret','set']
  with self.assertRaises(RuntimeError):self.execute()
  self.assertEqual(len([c for c in self.calls if c[0][:3]==['gh','secret','set']]),1)
  self.assertFalse(any(c[0][:3]==['gh','variable','set'] for c in self.calls))
 def test_noninteractive_rejected(self):
  with patch.object(m.sys.stdin,'isatty',return_value=False):
   with self.assertRaises(RuntimeError):m.main()
 def test_command_failure_hides_diagnostics(self):
  result=subprocess.CompletedProcess(['gh'],1,stdout=PASSWORD.encode(),stderr=PASSWORD.encode())
  with patch.object(m.subprocess,'run',return_value=result):
   with self.assertRaises(RuntimeError) as e:m.command(['gh','secret','set','AE_STORE_PASSWORD'],data=PASSWORD.encode())
  self.assertNotIn(PASSWORD,str(e.exception))
if __name__=='__main__':unittest.main()
