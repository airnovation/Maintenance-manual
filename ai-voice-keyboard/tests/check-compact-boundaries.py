from pathlib import Path
import xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
src=root/'app/src/main/java/com/airnovation/aivoicekeyboard'
service=(src/'VoiceKeyboardService.java').read_text()
settings=(src/'SettingsActivity.java').read_text()
manifest=ET.parse(root/'app/src/main/AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
assert {p.attrib[a+'name'] for p in manifest.findall('uses-permission')} == {'android.permission.INTERNET','android.permission.RECORD_AUDIO'}
services=manifest.findall('application/service')
assert len(services)==1 and services[0].attrib[a+'name']=='.VoiceKeyboardService'
for forbidden in ['VoiceDraftPanel','VisibleMessageService','AssistantPanel','旧版','undoLastTranslation','undoBefore','undoAfter', 'showDraft(', 'private TextView status, preview']:
 assert forbidden not in service, forbidden
assert 'ApiKeyValidation.validationMessage(input)' in settings
assert 'key.setSaveEnabled(false)' in settings
assert 'key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO)' in settings
assert 'getMessage()' not in service
assert 'onEvaluateFullscreenMode()' in service
assert 'Undo restores' not in settings
print('Compact structural boundaries passed (manifest, no obsolete UI/Undo, key entry privacy, sanitized service errors).')
