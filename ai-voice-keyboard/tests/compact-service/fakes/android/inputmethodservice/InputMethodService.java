package android.inputmethodservice;
import android.app.KeyguardManager;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.*;
public class InputMethodService extends Context {
public EditorInfo info; public InputConnection connection; public boolean shown=true, switchResult=true;
public int switches, keyEvents; public final KeyguardManager keyguard=new KeyguardManager();
public final InputMethodManager manager=new InputMethodManager();
public Object getSystemService(String name) { return KEYGUARD_SERVICE.equals(name) ? keyguard : manager; }
public EditorInfo getCurrentInputEditorInfo() { return info; }
public InputConnection getCurrentInputConnection() { return connection; }
public boolean isInputViewShown() { return shown; }
public boolean switchToPreviousInputMethod() { switches++; return switchResult; }
public void sendDownUpKeyEvents(int key) { if(key != 67) throw new AssertionError("Never send host actions"); keyEvents++; }
public View onCreateInputView() { return null; }
public boolean onEvaluateFullscreenMode() { return true; }
public void onStartInput(EditorInfo info,boolean restarting) { this.info=info; }
public void onStartInputView(EditorInfo info,boolean restarting) { this.info=info; }
public void onFinishInputView(boolean finishing) { }
public void onFinishInput() { }
public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidatesStart,int candidatesEnd) { }
public void onWindowShown() { }
public void onWindowHidden() { }
public void onDestroy() { }
}
