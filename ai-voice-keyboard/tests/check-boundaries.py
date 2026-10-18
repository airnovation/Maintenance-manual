from pathlib import Path
import xml.etree.ElementTree as ET
root = Path('app/src/main'); src = root/'java/com/airnovation/aivoicekeyboard'
transport = (src/'GeminiAssistantTransport.java').read_text()
assert 'https://generativelanguage.googleapis.com/v1beta/models/' in transport
assert 'SecureSettings.getApiKey(context, "gemini")' in transport
for forbidden in ('api.openai.com', 'OpenAiEnglish', 'GeminiEnglish.rewrite', 'Log.', 'System.out', 'bidiGenerateContent', 'WebSocket'):
    assert forbidden not in transport, forbidden
assert 'setInstanceFollowRedirects(false)' in transport
assert 'AssistantResponsePolicy.requireComplete(candidate.optString("finishReason", "")' in transport
assert 'session.token() != token' in transport
assert '65536' in transport and 'maxOutputTokens' in transport
service = (src/'VisibleMessageService.java').read_text()
for forbidden in ('performAction(', 'dispatchGesture(', 'performGlobalAction(', 'getNotification', 'event.getText();'):
    assert forbidden not in service, forbidden
callback = service.split('@Override public void onAccessibilityEvent')[1].split('private String capture')[0]
assert 'collect(' not in callback and 'getRootInActiveWindow(' not in callback
assert 'getMagnificationController().getScale()' in service
assert 'ancestorClip.contains(bounds)' in service
assert 'childClip.intersect(bounds)' in service
assert 'if (inList && AssistantNodeFilter.allowed' in service
assert 'if (unobscured && node.node.getText()' in service
assert 'AssistantNodeFilter.unobscured(' in service
assert 'visited[0] >= 250' in service
assert 'com.whatsapp:id/entry' in service
# Regression checks for the narrowly scoped SystemUI status-bar exception.
safe_status_bar = service.split('private boolean safeUnchangedStatusBarEvent')[1].split('private boolean unchangedStatusBarEvent')[0]
status_bar = service.split('private boolean unchangedStatusBarEvent')[1].split('private String windowGeometry')[0]
geometry = service.split('private String windowGeometry')[1].split('@Override public void onAccessibilityEvent')[0]
assert 'return unchangedStatusBarEvent(event);' in safe_status_bar
assert 'catch (RuntimeException ignored) { return false; }' in safe_status_bar
assert 'capturedWindowGeometry == null' in status_bar
assert 'event.getWindowId() < 0' in status_bar
assert 'capturedWindowGeometry.equals(windowGeometry(windows))' in status_bar
assert 'info.getId() != event.getWindowId()' in status_bar
assert 'info.getType() == AccessibilityWindowInfo.TYPE_SYSTEM' in status_bar
assert '!info.isFocused() && !info.isActive() && !bounds.isEmpty()' in status_bar
assert 'bounds.top == 0 && bounds.bottom <= displayHeight * 0.12' in status_bar
assert 'finally' in status_bar and 'info.recycle()' in status_bar
for forbidden in ('.getRoot(', '.getText(', '.getSource(', 'collect(', 'capture(', 'getRootInActiveWindow('):
    assert forbidden not in safe_status_bar + status_bar + geometry, forbidden
for field in ('info.getId()', 'info.getType()', 'info.getLayer()', 'info.isFocused()', 'info.isActive()', 'info.getBoundsInScreen(bounds)', 'bounds.toShortString()'):
    assert field in geometry, field
assert 'java.util.Collections.sort(parts)' in geometry
import re
assert re.search(r'if\s*\(\s*"com\.android\.systemui"\.equals\(pkg\)\s*&&\s*type == AccessibilityEvent\.TYPE_WINDOW_CONTENT_CHANGED\s*&&\s*safeUnchangedStatusBarEvent\(event\)\s*\)\s*return;', callback)
assert callback.rfind('session.invalidate();') > callback.find('safeUnchangedStatusBarEvent(event)')
capture = service.split('private String capture(')[1].split('private List<String> bounded')[0]
assert capture.index('capturedWindowGeometry = windowGeometry(windows);') > capture.index('if (!session.capture(')
# Local/screen size checks are eligibility gates before text access, never a substitute
# for ancestor containment or same-window/external-window occlusion checks.
collect = service.split('private void collect')[1].split('private static final class NodeText')[0]
assert 'if (message) node.getBoundsInParent(localBounds)' in collect
assert 'AssistantNodeFilter.sameUnclippedSize(box(bounds), box(localBounds))' in collect
assert 'fullyInside && unobscured && originalSize' in collect
assert '.getText(' not in collect
assert collect.index('sameUnclippedSize(') < collect.index('out.add(new NodeText(')
panel = (src/'AssistantPanel.java').read_text()
for forbidden in ('commitText(', 'performEditorAction(', 'AudioTranscriber', 'getCurrentInputConnection'):
    assert forbidden not in panel, forbidden
assert '!voice.isNetworkConnectionRequired()' in panel
assert 'consentPending' in panel and '同意并读取' in panel
assert 'ACTION_SCREEN_OFF' in panel
assert 'setTextIsSelectable(true)' in panel
recorder = (src/'AssistantRecorder.java').read_text()
assert '60000' in recorder and 'HttpURLConnection' not in recorder
ns = '{http://schemas.android.com/apk/res/android}'
xml = ET.parse(root/'res/xml/assistant_accessibility.xml').getroot()
assert 'typeNotificationStateChanged' not in xml.attrib[ns+'accessibilityEventTypes']
assert 'flagRetrieveInteractiveWindows' in xml.attrib[ns+'accessibilityFlags']
assert service.count('.getRoot()') == 1
assert 'other.getRoot(' not in service  # Other windows supply geometry only, never text roots.
assert 'Rect.intersects(bounds, covered)' in service
for path in root.rglob('*.xml'): ET.parse(path)
print('PASS structural consent/Gemini-only/no-send/offline-TTS/XML checks (not Android compilation)')
