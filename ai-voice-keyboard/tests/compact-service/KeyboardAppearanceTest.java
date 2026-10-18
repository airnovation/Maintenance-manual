package com.airnovation.aivoicekeyboard;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.*;
import static com.airnovation.aivoicekeyboard.CompactServiceTest.*;

/** Production appearance + actual service against explicit, synthetic Android state fakes. */
public final class KeyboardAppearanceTest {
    public static void main(String[] args) throws Exception {
        run("uiMode chooses soft gray/charcoal independent of unrelated mode bits", () -> {
            equal(KeyboardAppearance.LIGHT, KeyboardAppearance.forConfiguration(configuration(false)), "day palette");
            equal(KeyboardAppearance.DARK, KeyboardAppearance.forConfiguration(configuration(true)), "night palette");
            Configuration mode=configuration(true);mode.uiMode|=3;
            equal(KeyboardAppearance.DARK, KeyboardAppearance.forConfiguration(mode), "mask handles device mode bits");
            check(KeyboardAppearance.LIGHT.surface!=0xffffffff, "not pure white");
            check(KeyboardAppearance.DARK.surface!=0xff000000, "charcoal rather than pure black");
        });
        run("every state has readable text and monochrome icon contrast", () -> {
            for(KeyboardAppearance.Palette palette:List.of(KeyboardAppearance.LIGHT,KeyboardAppearance.DARK)) {
                check(contrast(palette.statusText,palette.surface)>=4.5,"status contrast >= 4.5:1");
                for(KeyboardAppearance.Role role:KeyboardAppearance.Role.values()) {
                    Harness h=new Harness();Button button=h.button("Mic");
                    KeyboardAppearance.style(button,palette,role,1f);
                    KeyboardAppearance.Colors colors=palette.colors(role);
                    for(String state:List.of("normal","pressed","focused","disabled","disabledPressed")) {
                        button.setEnabled(!state.startsWith("disabled"));
                        button.setPressed(state.equals("pressed")||state.equals("disabledPressed"));
                        button.setFocused(state.equals("focused"));
                        int fill=shape(button).color;
                        int expected=state.startsWith("disabled")?colors.disabled:
                                state.equals("normal")?colors.fill:colors.pressed;
                        equal(expected,fill,"correct background for "+role+" "+state);
                        int foreground=button.getCurrentTextColor();
                        check(contrast(foreground,fill)>=4.5,"readable "+role+" "+state+" contrast="+contrast(foreground,fill));
                        if(role!=KeyboardAppearance.Role.NEUTRAL) {
                            Drawable icon=button.getCompoundDrawablesRelative()[0];
                            Canvas canvas=new Canvas();icon.draw(canvas);
                            equal(foreground,canvas.color,"icon tracks enabled/disabled text color");
                            equal(18,icon.getIntrinsicHeight(),"compact 18dp icon");
                            equal(role==KeyboardAppearance.Role.STOP?0:1,canvas.arcs,"microphone/stop silhouettes differ");
                        }
                        System.out.printf(Locale.ROOT,"CONTRAST %s %s %s %.2f:1%n",
                                palette==KeyboardAppearance.LIGHT?"light":"dark",role,state,contrast(foreground,fill));
                    }
                }
            }
        });
        run("four touch targets keep weighted wrap-content bounds, internal spacing and nav insets", () -> {
            Harness h=new Harness();equal(List.of("Mic","EN 英文","⌫ Delete","↩ Keyboard"),h.labels(),"exact controls");
            equal(3,h.root.children.size(),"no added view/row");
            for(View view:h.views())if(view instanceof Button button) {
                LinearLayout parent=(LinearLayout)button.parent;
                LinearLayout.LayoutParams params=parent.params.get(button);
                equal(0,params.width,"same equal-width layout");equal(-2,params.height,"same wrap-content height");
                equal(1f,params.weight,"same weight");equal(48,button.getMinimumHeight(),"48dp touch target retained");
                equal(16,button.getPaddingLeft(),"theme left text padding retained");equal(0,button.getPaddingTop(),"theme top text padding retained");
                equal(16,button.getPaddingRight(),"theme right text padding retained");equal(0,button.getPaddingBottom(),"theme bottom text padding retained");
                InsetDrawable background=(InsetDrawable)button.getBackground().getCurrent();
                equal(3,background.left,"internal horizontal gap");equal(3,background.right,"symmetric gap");
                equal(6,background.top,"same baseline vertical drawable inset");equal(6,background.bottom,"symmetric vertical inset");
                equal(12f,shape(button).radius,"consistent corner radius");
            }
            WindowInsets insets=new WindowInsets();insets.bottom=30;h.root.insetsListener.apply(h.root,insets);
            equal(42,h.root.padBottom,"navigation bar + 12dp padding preserved");
            h.service.onConfigurationChanged(configuration(true));
            equal(42,h.root.padBottom,"theme update never rewrites applied nav insets");
            insets.bottom=0;h.root.insetsListener.apply(h.root,insets);equal(24,h.root.padBottom,"minimum bottom padding retained");
            equal(15f,h.status.textSize,"status font never shrinks");equal(2,h.status.maxLines,"important status can use two lines");
        });
        for(String phase:List.of("idle","recording","processing","translating")) {
            run("in-place uiMode repaint preserves "+phase+" state, guards and single action", () -> {
                Harness h=new Harness();prepare(h,phase);
                List<Object> state=snapshot(h);String status=h.status.getText().toString();
                int starts=h.recorder.startCalls, finishes=h.recorder.finishCalls;
                int cancels=h.recorder.cancelCalls, transportCancels=h.transport.cancelCalls;
                for(boolean dark:new boolean[]{true,false,true}) {
                    h.service.onConfigurationChanged(configuration(dark));
                    equal(state,snapshot(h),"theme paint preserves tokens, selection guards, session, button enables");
                    equal(status,h.status.getText().toString(),"theme paint preserves informative status");
                    equal(dark?KeyboardAppearance.DARK.surface:KeyboardAppearance.LIGHT.surface,h.root.backgroundColor,"surface repainted");
                    equal(starts,h.recorder.startCalls,"never starts recording");equal(finishes,h.recorder.finishCalls,"never stops/uploads");
                    equal(cancels,h.recorder.cancelCalls,"no independent cancel");equal(transportCancels,h.transport.cancelCalls,"no request cancellation by paint");
                    equal(0,h.host.commits,"never inserts during repaint");
                }
                if(phase.equals("recording")) {check(h.recorder.active,"still recording under no-restart fake");h.finish();h.complete("Speech","ONE");equal(1,h.host.commits,"original Stop still inserts once");}
                if(phase.equals("processing")) {check(!h.button("Mic").performClick(),"processing still blocks duplicate request");h.complete("Speech","ONE");h.complete("Again","TWO");equal(1,h.host.commits,"old request completes once");}
                if(phase.equals("translating")) {check(!h.button("EN 英文").performClick(),"EN remains guarded");OpenAiEnglish.complete(0,"RESULT",null);equal("RESULT",h.host.text,"original EN callback retained");}
                if(!phase.equals("translating"))h.assertNoVoiceHostRead();
            });
            run("framework lifecycle restart during "+phase+" theme change cancels stale work safely", () -> {
                Harness h=new Harness();prepare(h,phase);h.service.recreateOnConfiguration=true;
                h.service.onConfigurationChanged(configuration(true));
                h.root=(LinearLayout)h.service.recreatedView;h.status=(TextView)field(h.service,"status");
                equal(KeyboardAppearance.DARK.surface,h.root.backgroundColor,"recreated view matches night mode");
                equal(List.of("Mic","EN 英文","⌫ Delete","↩ Keyboard"),h.labels(),"recreated controls stable");
                check(!h.recorder.active,"existing lifecycle cancellation releases mic");
                if(phase.equals("processing"))h.complete("STALE","DRAFT");
                if(phase.equals("translating"))OpenAiEnglish.complete(0,"STALE",null);
                equal(0,h.host.commits,"stale callbacks cannot revive cancelled operation");h.assertClear();
                equal(phase.equals("processing")?1:0,h.transport.requests.size(),"no extra network submission");
            });
        }
        run("selection/lifecycle guards remain active after night repaint", () -> {
            for(String phase:List.of("recording","processing")) {
                Harness h=new Harness();prepare(h,phase);h.service.onConfigurationChanged(configuration(true));
                h.update("sameOffsets");if(phase.equals("processing"))h.complete("STALE","DRAFT");
                equal(0,h.host.commits,"same-offset edit invalidates themed operation");h.assertClear();h.assertNoVoiceHostRead();
            }
        });
        run("unavailable editor stays disabled after restyling; Keyboard still cancels", () -> {
            Harness h=new Harness();h.service.info.inputType=129;h.service.onStartInput(h.service.info,false);
            h.service.onConfigurationChanged(configuration(true));
            check(!h.button("Mic").isEnabled()&&!h.button("EN 英文").isEnabled()&&!h.button("⌫ Delete").isEnabled(),"no disabled control revival");
            check(h.button("↩ Keyboard").isEnabled(),"switching remains available");
            Harness p=new Harness();p.start();p.service.onConfigurationChanged(configuration(true));
            p.click("↩ Keyboard");check(!p.recorder.active,"Keyboard cancels after restyling");equal(0,p.transport.requests.size(),"cancel does not upload");
        });
        run("compact status retains important recording, upload, language and error meaning", () -> {
            Harness h=new Harness();String idle=h.status.getText().toString();
            check(!idle.contains("\n"),"ordinary idle has no forced second line");
            check(idle.contains("默认英文")&&idle.contains("用中文")&&idle.contains("手动发送"),"language override and manual send stay visible");
            check(h.button("Mic").getContentDescription().toString().contains("内容和语气"),"Mic accessible description retains instructions");
            h.start();String recording=h.status.getText().toString();
            for(String important:List.of("Stop","Gemini","填入","Keyboard取消","60秒","不自动上传"))check(recording.contains(important),"recording retains "+important);
            h.finish();String processing=h.status.getText().toString();
            for(String important:List.of("Gemini","Keyboard可取消","无法撤回"))check(processing.contains(important),"processing retains "+important);
            for(int code:new int[]{401,403,429}) {
                String full=SafeApiErrors.message(SafeApiErrors.http(code));String compact=KeyboardAppearance.statusCopy(full);
                check(compact.contains("HTTP "+code),"HTTP code retained");check(compact.length()<full.length(),"fixed display copy more compact");
                if(code!=429)check(compact.contains("密钥")&&compact.contains("权限"),"access remediation retained");
                else check(compact.contains("账单/额度")&&compact.contains("重试"),"quota remediation retained");
            }
            String keyError=ApiKeyValidation.validationMessage("bad");
            String compact=KeyboardAppearance.statusCopy(keyError);
            for(String important:List.of("密钥","空白","换行","引号","错误文字"))check(compact.contains(important),"key-format advice retained: "+important);
            h.transport.complete(0,null,null,keyError);
            equal(compact,h.status.getText().toString(),"short fixed error displayed");equal(keyError,h.status.getContentDescription(),"full original safe error available to accessibility");
            equal("Unknown synthetic safe status",KeyboardAppearance.statusCopy("Unknown synthetic safe status"),"unknown safe text preserved");
        });
        System.out.println("PASS appearance + configuration regression: "+cases+" cases, "+assertions+" assertions (host states, not device rendering)");
    }
    static Configuration configuration(boolean dark) {Configuration c=new Configuration();c.uiMode=dark?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO;return c;}
    static GradientDrawable shape(Button button) {return (GradientDrawable)((InsetDrawable)button.getBackground().getCurrent()).getDrawable();}
    static void prepare(Harness h,String phase)throws Exception {
        if(phase.equals("recording")||phase.equals("processing"))h.start();
        if(phase.equals("processing"))h.finish();
        if(phase.equals("translating")){h.setHost("SOURCE",0,0);h.click("EN 英文");}
    }
    static List<Object> snapshot(Harness h)throws Exception {
        List<Object> result=new ArrayList<>();
        for(String field:List.of("generation","voiceToken","phase","selectionEpoch","selectionVersion","voiceSelectionVersion","voiceCursor"))result.add(field(h.service,field));
        result.add(h.session.draft());result.add(h.session.transcript());result.add(h.labels());
        for(View view:h.views())if(view instanceof Button)result.add(view.isEnabled());
        result.add(h.transport.requests.size());return result;
    }
    static double contrast(int first,int second) {double a=luminance(first),b=luminance(second);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
    static double luminance(int rgb) {return .2126*linear((rgb>>16)&255)+.7152*linear((rgb>>8)&255)+.0722*linear(rgb&255);}
    static double linear(int channel) {double c=channel/255.;return c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4);}
}
