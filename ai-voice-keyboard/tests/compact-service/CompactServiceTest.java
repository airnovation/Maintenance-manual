package com.airnovation.aivoicekeyboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.View;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/** Actual service/session/binding. Android, credentials, audio and HTTP are synthetic only. */
public final class CompactServiceTest {
    interface Checked { void run() throws Exception; }
    static int cases, assertions;
    static final List<Harness> harnesses = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        run("four compact controls, no preview/confirmation or opening side effects",()->{
            Harness h=new Harness();equal(List.of("Mic","EN 英文","⌫ Delete","↩ Keyboard"),h.labels(),"exact four controls");
            equal(3,h.root.children.size(),"only status and two rows");equal(2,((LinearLayout)h.root.children.get(1)).children.size(),"two primary controls");
            equal(12,h.root.padLeft,"original padding");equal(24,h.root.padBottom,"original bottom padding");equal(2,h.status.maxLines,"bounded status");
            check(!h.root.saveEnabled,"RAM-only UI");check(!h.service.onEvaluateFullscreenMode(),"no extracted fullscreen editor");
            equal(0,h.recorder.startCalls,"opening never records");equal(0,h.transport.requests.size(),"opening never uploads");equal(0,h.host.commits,"opening never commits");h.assertNoVoiceHostRead();
        });
        run("explicit Mic Stop automatically inserts once without host reads or send",()->{
            Harness h=new Harness();h.start();equal(0,h.transport.requests.size(),"Mic alone no upload");
            check(!h.button("EN 英文").performClick()&&!h.button("⌫ Delete").performClick(),"recording excludes EN Delete");
            h.finish();equal(1,h.transport.requests.size(),"Stop uploads once");equal(null,h.transport.requests.get(0).previous(),"no previous draft/context");
            check(!h.button("Mic").performClick()&&!h.button("EN 英文").performClick(),"processing excludes repeat requests");
            h.complete("Synthetic speech","Synthetic message");equal(1,h.host.commits,"automatic insertion once");equal("Synthetic message",h.host.lastCommit,"intended draft committed");
            equal(1,h.host.finishComposingCalls,"composition finalized only immediately before commit");h.assertNoVoiceHostRead();h.assertClear();
            h.complete("Duplicate","DUPLICATE");equal(1,h.host.commits,"duplicate callback cannot repeat insertion");equal(0,h.service.keyEvents,"never sends host action");
        });
        run("new Mic has no previous text or revision mode",()->{
            Harness h=new Harness();h.start();h.finish();h.complete("Speech","ONE");h.start();h.finish();
            equal(null,h.transport.requests.get(1).previous(),"fresh recording carries no prior draft");equal("",h.session.draft(),"prior RAM draft gone");h.assertNoVoiceHostRead();
        });
        run("selected range and unknown cursor block voice before composition or recording",()->{
            Harness h=new Harness();h.setHost("ORIGINAL",1,5);h.start();equal(0,h.recorder.startCalls,"selected range rejected");equal(0,h.host.finishComposingCalls,"selection never mutated");
            h.setHost("ORIGINAL",-1,-1);h.start();equal(0,h.recorder.startCalls,"unknown cursor rejected");equal("ORIGINAL",h.host.text,"original unchanged");h.assertNoVoiceHostRead();
        });
        run("preexisting composing span preserved at collapsed cursor",()->{
            Harness h=new Harness();h.setHost("existing",8,8);h.host.compStart=0;h.host.compEnd=8;
            h.start();equal("existing",h.host.text,"finishComposing preserves existing text");h.finish();h.complete("Speech"," plus voice");
            equal("existing plus voice",h.host.text,"voice appended instead of replacing composing text");h.assertNoVoiceHostRead();
        });
        run("silent composing span before completion is finalized rather than replaced",()->{
            Harness h=new Harness();h.setHost("existing",8,8);h.start();h.finish();h.host.compStart=0;h.host.compEnd=8;h.complete("Speech"," added");
            equal("existing added",h.host.text,"final precommit composition check preserves span");h.assertNoVoiceHostRead();
        });
        run("Mic never mutates composing state before Stop",()->{
            Harness h=new Harness();h.setHost("existing",8,8);h.host.compStart=0;h.host.compEnd=8;
            h.start();equal(0,h.host.finishComposingCalls,"no initial composition call");equal(0,h.host.compStart,"span retained while recording");equal("existing",h.host.text,"text unchanged");h.assertNoVoiceHostRead();
        });
        run("composition failure before insertion drops result instead of retrying",()->{
            Harness h=new Harness();h.start();h.finish();h.host.finishComposingResult=false;h.complete("Speech","DRAFT");equal(0,h.host.commits,"no unsafe commit");h.assertClear();
            h.complete("Speech","DRAFT");equal(0,h.host.commits,"callback cannot retry");
        });
        run("synchronous own composition clear at unchanged cursor permits one commit",()->{
            Harness h=new Harness();h.setHost("existing",8,8);h.host.compStart=0;h.host.compEnd=8;
            h.service.onUpdateSelection(8,8,8,8,0,8);h.start();h.finish();
            h.host.onFinishComposing=()->h.service.onUpdateSelection(8,8,8,8,-1,-1);
            h.complete("Speech"," added");equal("existing added",h.host.text,"known composition finalized and preserved");equal(1,h.host.commits,"one commit");h.assertNoVoiceHostRead();
        });
        run("queued composition clear after commit updates idle state without duplicate insertion",()->{
            Harness h=new Harness();h.setHost("existing",8,8);h.host.compStart=0;h.host.compEnd=8;h.start();h.finish();
            h.complete("Speech"," added");h.service.onUpdateSelection(8,8,8,8,-1,-1);h.service.onUpdateSelection(8,8,14,14,-1,-1);
            equal("existing added",h.host.text,"queued clear keeps inserted result");equal(1,h.host.commits,"no duplicate commit");h.assertClear();
        });
        run("sync cursor range composition or lifecycle change during finalization blocks commit",()->{
            for(String change:List.of("cursor","selection","composition","lifecycle")){
                Harness h=new Harness();h.start();h.finish();
                h.host.onFinishComposing=()->{if(change.equals("lifecycle"))h.service.onStartInput(new EditorInfo(),false);else h.update(change);};
                h.complete("Speech","DRAFT");equal(0,h.host.commits,"no commit after "+change);h.assertClear();
            }
        });
        run("composition finalization exception does not attempt insertion",()->{
            Harness h=new Harness();h.start();h.finish();h.host.throwFinishComposing=true;h.complete("Speech","DRAFT");equal(0,h.host.commits,"exception blocks commit");h.assertClear();
        });
        for(String update:List.of("cursor","selection","sameOffsets","composition")) {
            run(update+" signal cancels recording and processing without reading text",()->{
                Harness h=new Harness();h.start();h.update(update);check(!h.recorder.active,"recording cancelled");equal(0,h.transport.requests.size(),"cancel before Stop no upload");h.assertClear();h.assertNoVoiceHostRead();
                Harness p=new Harness();p.start();p.finish();p.update(update);p.complete("STALE","DRAFT");equal(0,p.host.commits,"changed target never receives result");p.assertClear();p.assertNoVoiceHostRead();
            });
        }
        run("provider and key validation block recording and never show key",()->{
            Harness h=new Harness();SharedPreferences.provider="openai";h.start();equal(0,h.recorder.startCalls,"Mic remains explicitly Gemini-only");equal(0,SecureSettings.reads,"wrong provider skips key read");
            SharedPreferences.provider="gemini";SecureSettings.key="bad\nSYNTHETIC_SECRET";h.start();equal(0,h.recorder.startCalls,"bad key blocks");check(!h.status.getText().toString().contains("SYNTHETIC_SECRET"),"key not echoed");h.assertNoVoiceHostRead();
        });
        for(int type:new int[]{0,2,18,129,145,225})run("disallowed editor type "+type+" disables voice EN",()->{
            Harness h=new Harness();h.service.info.inputType=type;h.service.onStartInput(h.service.info,false);check(!h.button("Mic").performClick()&&!h.button("EN 英文").performClick(),"controls disabled");equal(0,SecureSettings.reads,"key unread");h.assertNoVoiceHostRead();
        });
        run("missing field and lock prevent capture",()->{
            Harness h=new Harness();h.service.keyguard.locked=true;h.start();equal(0,h.recorder.startCalls,"lock blocks");h.service.keyguard.locked=false;h.service.onStartInput(null,false);check(!h.button("Mic").performClick(),"missing editor blocked");
        });
        run("provider change and 60 second limit cancel without automatic upload",()->{
            Harness h=new Harness();h.start();SharedPreferences.provider="openai";h.finish();equal(0,h.transport.requests.size(),"provider changed no upload");h.assertClear();
            SharedPreferences.provider="gemini";Harness p=new Harness();p.start();p.recorder.limit.run();equal(0,p.transport.requests.size(),"limit never acts as Stop");check(!p.recorder.active,"limit releases mic");p.assertClear();
        });
        run("failed or short audio cannot upload",()->{
            Harness h=new Harness();h.recorder.startError="Synthetic permission failure";h.start();h.assertClear();h.recorder.startError=null;h.start();h.recorder.finishError="Synthetic interruption";h.finish();
            equal(0,h.transport.requests.size(),"bad audio never sent");check(!h.recorder.file.exists(),"bad audio deleted");h.assertClear();
        });
        for(String exit:List.of("keyboard","finishView","finishInput","hidden","lock","destroy","newEditor","recreate")) {
            run(exit+" cancels capture without upload and suppresses late result",()->{
                Harness h=new Harness();h.start();h.exit(exit);check(!h.recorder.active,"capture cancelled");equal(0,h.transport.requests.size(),"cancel no upload");h.assertClear();
                Harness p=new Harness();p.start();p.finish();p.exit(exit);p.complete("LATE","DRAFT");equal(0,p.host.commits,"late response never inserts");p.assertClear();
            });
        }
        for(String change:List.of("connection","field","package","type","hidden","locked"))run("unannounced "+change+" invalidates original binding",()->{
            Harness h=new Harness();h.start();h.change(change);h.finish();equal(0,h.transport.requests.size(),"stale Stop no upload");h.assertClear();
            Harness p=new Harness();p.start();p.finish();p.change(change);p.complete("STALE","DRAFT");equal(0,p.host.commits,"stale completion no insert");p.assertClear();
        });
        run("deferred recorder callback after exit deletes audio without upload",()->{
            Harness h=new Harness();h.recorder.deferFinish=true;h.start();h.finish();File audio=h.recorder.file;h.click("↩ Keyboard");h.recorder.completeFinish();check(!audio.exists(),"stale file deleted");equal(0,h.transport.requests.size(),"not uploaded");
        });
        run("malformed results and errors never insert",()->{
            Harness h=new Harness();h.start();h.finish();h.complete("x".repeat(3001),"DRAFT");h.assertClear();h.start();h.finish();h.transport.complete(1,"Speech",null,"Synthetic safe failure");h.assertClear();
            h.start();h.finish();h.complete("Speech","x".repeat(4001));h.assertClear();equal(0,h.host.commits,"all bad results rejected");
        });
        run("old and duplicate callbacks cannot insert newer session twice",()->{
            Harness h=new Harness();h.start();h.finish();h.click("↩ Keyboard");h.start();h.finish();h.transport.complete(0,"Old","OLD",null);equal(0,h.host.commits,"old suppressed");
            h.transport.complete(1,"New","NEW",null);h.transport.complete(1,"Again","DUPLICATE",null);equal(1,h.host.commits,"new result commits exactly once");equal("NEW",h.host.lastCommit,"only new result");
        });
        run("host rejection or uncertain commit drops result and never retries",()->{
            Harness h=new Harness();h.host.commitResult=false;h.start();h.finish();h.complete("Speech","DRAFT");h.assertClear();h.complete("Speech","DRAFT");equal(1,h.host.commits,"rejection no repeated commit");
            Harness p=new Harness();p.host.throwCommit=true;p.start();p.finish();p.complete("Speech","DRAFT");p.assertClear();p.complete("Speech","DRAFT");equal(1,p.host.commits,"uncertain result no retry");check(!p.status.getText().toString().contains("UNSAFE_EXCEPTION"),"raw exception hidden");
        });
        run("own commit selection callback is allowed but lifecycle change cannot revive result",()->{
            Harness h=new Harness();h.host.onCommit=()->h.service.onUpdateSelection(0,0,5,5,-1,-1);h.start();h.finish();h.complete("Speech","DRAFT");equal(1,h.host.commits,"own commit accepted once");h.assertClear();
            Harness p=new Harness();p.host.onCommit=()->p.service.onStartInput(new EditorInfo(),false);p.start();p.finish();p.complete("Speech","DRAFT");p.assertClear();equal(1,p.host.commits,"lifecycle callback no retry");
        });
        run("EN preserves whole typed replacement and excludes Mic while busy",()->{
            Harness h=new Harness();h.setHost("Synthetic typed content",23,23);h.click("EN 英文");equal("Synthetic typed content",OpenAiEnglish.requests.get(0).source(),"explicit EN source");
            check(!h.button("Mic").performClick()&&!h.button("EN 英文").performClick()&&!h.button("⌫ Delete").performClick(),"EN mutual exclusion");OpenAiEnglish.complete(0,"English result",null);equal("English result",h.host.text,"unchanged text replaced");equal(1,h.host.commits,"one commit");h.assertClear();
        });
        run("EN only translates selected substring when selection exists",()->{
            Harness h=new Harness();h.setHost("before SOURCE after",7,13);h.click("EN 英文");equal("SOURCE",OpenAiEnglish.requests.get(0).source(),"only selection transmitted");OpenAiEnglish.complete(0,"RESULT",null);equal("before RESULT after",h.host.text,"outside selection preserved");
        });
        run("EN preserves nonselected composing text and intended replacement range",()->{
            Harness h=new Harness();h.setHost("before SOURCE after",7,13);h.host.compStart=0;h.host.compEnd=6;
            h.click("EN 英文");equal("SOURCE",OpenAiEnglish.requests.get(0).source(),"selected range remains authoritative");
            h.host.compStart=14;h.host.compEnd=19;OpenAiEnglish.complete(0,"RESULT",null);
            equal("before RESULT after",h.host.text,"composing span outside selection never overwritten");equal(2,h.host.finishComposingCalls,"EN finalizes before snapshot and replacement");
        });
        run("EN composition failure and synchronous lifecycle changes cannot dispatch or commit",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.host.finishComposingResult=false;h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"initial finish failure no request");
            Harness p=new Harness();p.setHost("SOURCE",0,0);p.host.onFinishComposing=()->p.service.onStartInput(new EditorInfo(),false);p.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"initial lifecycle change no request");
            Harness q=new Harness();q.setHost("SOURCE",0,0);q.click("EN 英文");q.host.onFinishComposing=()->q.service.onStartInput(new EditorInfo(),false);OpenAiEnglish.complete(0,"STALE",null);equal(0,q.host.commits,"late finish lifecycle change no commit");
        });
        run("changed text or cursor aborts EN without fallback UI",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.click("EN 英文");h.host.text="EDITED";OpenAiEnglish.complete(0,"RESULT",null);equal(0,h.host.commits,"changed text not overwritten");h.assertClear();
            Harness p=new Harness();p.setHost("SOURCE",0,0);p.click("EN 英文");p.host.start=p.host.end=2;OpenAiEnglish.complete(1,"RESULT",null);equal(0,p.host.commits,"changed cursor not overwritten");p.assertClear();
        });
        run("EN failure preserves original with short control-free status",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.click("EN 英文");OpenAiEnglish.complete(0,null,"Fixed safe error\n\n\u202e"+"x".repeat(300));equal("SOURCE",h.host.text,"source retained");h.assertClear();
            String value=h.status.getText().toString();check(value.length()<=180,"status bounded");equal(1L,value.chars().filter(c->c=='\n').count(),"max two lines");check(!value.contains("\u202e"),"bidi controls removed");
        });
        run("partial oversized and null EN extractions cannot be sent",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.host.partialStart=1;h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"partial refused");h.host.partialStart=-1;h.host.text="x".repeat(8001);h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"oversized refused");h.host.nullExtraction=true;h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"null refused");
        });
        run("synchronous lifecycle inside extraction blocks EN request and callback",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.host.onExtract=()->h.service.onStartInput(new EditorInfo(),false);h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"no stale dispatch");h.assertClear();
            Harness p=new Harness();p.setHost("SOURCE",0,0);p.click("EN 英文");p.host.onExtract=()->p.service.onStartInput(new EditorInfo(),false);OpenAiEnglish.complete(0,"STALE",null);equal(0,p.host.commits,"no stale callback commit");p.assertClear();
        });
        run("connection swap during extraction or lifecycle during selection blocks EN",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.host.onExtract=()->h.service.connection=new Host();h.click("EN 英文");equal(0,OpenAiEnglish.requests.size(),"connection swap no dispatch");
            Harness p=new Harness();p.setHost("SOURCE",0,0);p.click("EN 英文");p.host.onSelect=()->p.service.onStartInput(new EditorInfo(),false);OpenAiEnglish.complete(0,"STALE",null);equal(0,p.host.commits,"no commit after selection lifecycle change");p.assertClear();
        });
        run("late EN after exit never affects editor",()->{
            Harness h=new Harness();h.setHost("SOURCE",0,0);h.click("EN 英文");h.service.onFinishInput();OpenAiEnglish.complete(0,"STALE",null);h.assertClear();equal(0,h.host.commits,"late EN ignored");
        });
        for(String provider:List.of("gemini","openai")) {
            for(String exit:List.of("keyboard","finishView","finishInput","hidden","lock","destroy","newEditor","recreate")) {
                run(provider+" EN "+exit+" cancels its handle and rejects late callbacks",()->{
                    Harness h=new Harness();SharedPreferences.provider=provider;h.setHost("SOURCE",0,0);h.click("EN 英文");
                    EnglishRequest request=OpenAiEnglish.requests.get(0).handle();equal(request,field(h.service,"englishRequest"),"pending handle retained");
                    h.exit(exit);check(request.cancelled,"request cancelled on exit");equal(1,request.cancelCalls,"detached handle cancelled once");
                    equal(null,field(h.service,"englishRequest"),"cancelled handle released");
                    OpenAiEnglish.complete(0,"STALE",null);OpenAiEnglish.complete(0,null,"Stale error");
                    equal("SOURCE",h.host.text,"original retained");equal(0,h.host.commits,"late success/error no insertion");
                    equal(0,h.service.keyEvents,"never sends");h.assertClear();
                });
            }
            run(provider+" reset starts a new EN without retaining the abandoned request",()->{
                Harness h=new Harness();SharedPreferences.provider=provider;h.setHost("SOURCE",0,0);h.click("EN 英文");
                EnglishRequest old=OpenAiEnglish.requests.get(0).handle();
                check(!h.button("EN 英文").performClick(),"repeat tap disabled");equal(1,OpenAiEnglish.requests.size(),"repeat tap no second request");
                h.click("↩ Keyboard");h.setHost("NEW SOURCE",0,0);h.click("EN 英文");
                EnglishRequest current=OpenAiEnglish.requests.get(1).handle();check(old.cancelled,"abandoned request cancelled");check(!current.cancelled,"new request active");
                equal(current,field(h.service,"englishRequest"),"only new handle retained");
                OpenAiEnglish.complete(0,"OLD",null);OpenAiEnglish.complete(0,null,"Old failure");
                check(!current.cancelled,"old callback cannot cancel current");equal(0,h.host.commits,"old cannot insert");
                OpenAiEnglish.complete(1,"NEW RESULT",null);OpenAiEnglish.complete(1,"DUPLICATE",null);
                equal("NEW RESULT",h.host.text,"new result inserted");equal(1,h.host.commits,"new inserts exactly once");
                equal(null,field(h.service,"englishRequest"),"completed handle released");equal(0,h.service.keyEvents,"manual send only");
            });
            run(provider+" synchronous result before rewrite returns cannot retain a finished handle",()->{
                Harness h=new Harness();SharedPreferences.provider=provider;h.setHost("SOURCE",0,0);
                OpenAiEnglish.duringRewrite=request->request.callback().done("RESULT",null);
                h.click("EN 英文");EnglishRequest request=OpenAiEnglish.requests.get(0).handle();
                equal("RESULT",h.host.text,"synchronous result inserted once");equal(1,h.host.commits,"one insertion");
                check(request.cancelled,"already completed returned handle cancelled");equal(null,field(h.service,"englishRequest"),"finished request not retained");
                OpenAiEnglish.duringRewrite=null;h.setHost("SECOND",0,0);h.click("EN 英文");
                check(!OpenAiEnglish.requests.get(1).handle().cancelled,"next request independent");
            });
            run(provider+" lifecycle during dispatch cannot replace a newer request handle",()->{
                Harness h=new Harness();SharedPreferences.provider=provider;h.setHost("SOURCE",0,0);
                OpenAiEnglish.duringRewrite=request->{
                    OpenAiEnglish.duringRewrite=null;
                    h.setHost("NEW SOURCE",0,0);h.click("EN 英文");
                };
                h.click("EN 英文");EnglishRequest old=OpenAiEnglish.requests.get(0).handle(),current=OpenAiEnglish.requests.get(1).handle();
                check(old.cancelled,"old dispatch cancelled after generation change");check(!current.cancelled,"new reentrant dispatch active");
                equal(current,field(h.service,"englishRequest"),"old return cannot overwrite newer handle");
                OpenAiEnglish.complete(0,"OLD",null);equal(0,h.host.commits,"old callback ignored");
                OpenAiEnglish.complete(1,"NEW",null);equal("NEW",h.host.text,"new callback accepted");equal(1,h.host.commits,"exactly one insertion");
            });
        }
        for(String change:List.of("text","cursor","selection","connection","field","package","type","hidden","locked")) {
            run("EN changed "+change+" rejects result and releases request",()->{
                Harness h=new Harness();h.setHost("SOURCE",0,0);h.click("EN 英文");EnglishRequest request=OpenAiEnglish.requests.get(0).handle();
                switch(change){case "text"->h.host.text="EDITED";case "cursor"->h.host.start=h.host.end=2;case "selection"->{h.host.start=1;h.host.end=4;}default->h.change(change);}
                OpenAiEnglish.complete(0,"STALE",null);check(request.cancelled,"invalidated result cancels handle");
                equal(null,field(h.service,"englishRequest"),"invalid handle released");equal(0,h.host.commits,"changed target not overwritten");
            });
        }
        run("Delete remains explicit without AI requests",()->{
            Harness h=new Harness();h.setHost("abcd",1,3);h.click("⌫ Delete");equal("ad",h.host.text,"selected text removed");equal(0,h.transport.requests.size(),"no voice request");equal(0,OpenAiEnglish.requests.size(),"no translation");
        });
        run("Delete fallback and selected read cannot target synchronously changed editor",()->{
            Harness h=new Harness();h.host.deleteResult=false;h.host.onDelete=()->h.service.onStartInput(new EditorInfo(),false);h.click("⌫ Delete");equal(0,h.service.keyEvents,"no fallback after lifecycle");
            Harness p=new Harness();p.host.deleteResult=false;p.host.onDelete=()->p.service.connection=new Host();p.click("⌫ Delete");equal(0,p.service.keyEvents,"no fallback after connection change");
            Harness q=new Harness();q.setHost("abcd",1,3);q.host.onSelectedRead=()->q.service.onStartInput(new EditorInfo(),false);q.click("⌫ Delete");equal(0,q.host.commits,"selected read cannot delete after lifecycle change");
        });
        run("normal Delete and Keyboard picker fallback retained",()->{
            Harness h=new Harness();h.host.deleteResult=false;h.click("⌫ Delete");equal(1,h.service.keyEvents,"one Delete fallback");h.start();h.service.switchResult=false;h.click("↩ Keyboard");check(!h.recorder.active,"cancel before switching");equal(1,h.service.manager.pickerCalls,"picker fallback retained");
        });
        System.out.println("PASS actual compact auto-insert service: "+cases+" cases, "+assertions+" assertions (synthetic boundaries only)");
    }
    static void run(String name,Checked test)throws Exception{SharedPreferences.provider="gemini";SecureSettings.reset();OpenAiEnglish.reset();try{test.run();cases++;System.out.println("PASS "+name);}catch(Throwable failure){throw new AssertionError("FAIL "+name+": "+failure.getMessage(),failure);}finally{for(Harness h:harnesses){h.service.onDestroy();if(h.recorder.file!=null)h.recorder.file.delete();}harnesses.clear();OpenAiEnglish.duringRewrite=null;}}
    static void check(boolean value,String reason){assertions++;if(!value)throw new AssertionError(reason);}
    static void equal(Object expected,Object actual,String reason){check(Objects.equals(expected,actual),reason+"; expected="+expected+", actual="+actual);}
    static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    static final class Harness{
        final VoiceKeyboardService service=new VoiceKeyboardService();final Host host=new Host();LinearLayout root;TextView status;
        final VoiceDraftSession session;final VoiceDraftTransport transport;final AssistantRecorder recorder;
        Harness()throws Exception{harnesses.add(this);service.info=new EditorInfo();service.connection=host;root=(LinearLayout)service.onCreateInputView();service.onStartInput(service.info,false);service.onStartInputView(service.info,false);status=(TextView)field(service,"status");session=(VoiceDraftSession)field(service,"session");transport=VoiceDraftTransport.latest;recorder=AssistantRecorder.latest;}
        void setHost(String text,int start,int end){host.text=text;host.start=start;host.end=end;service.info.initialSelStart=start;service.info.initialSelEnd=end;service.onStartInput(service.info,false);}
        List<View> views(){List<View> r=new ArrayList<>();collect(root,r);return r;}void collect(View v,List<View> r){r.add(v);if(v instanceof LinearLayout l)for(View c:l.children)collect(c,r);}
        List<String> labels(){return views().stream().filter(Button.class::isInstance).map(v->((Button)v).getText().toString()).toList();}
        Button button(String label){for(View v:views())if(v instanceof Button b&&label.contentEquals(b.getText()))return b;throw new AssertionError("Missing "+label);}
        void click(String label){check(button(label).isShown(),label+" visible");check(button(label).performClick(),label+" enabled");}void start(){click("Mic");}
        void finish()throws Exception{recorder.file=File.createTempFile("compact-auto-synthetic-", ".m4a");click("Stop");}
        void complete(String transcript,String draft){transport.complete(transport.requests.size()-1,transcript,draft,null);}
        void assertNoVoiceHostRead(){equal(0,host.extractedReads,"no voice extracted text read");equal(0,host.selectedReads,"no voice selected text read");}
        void assertClear(){equal("",session.draft(),"RAM draft cleared");equal("",session.transcript(),"RAM transcript cleared");check(!status.getText().toString().contains("DRAFT"),"no draft rendered");equal(3,root.children.size(),"no fallback view added");}
        void update(String kind){switch(kind){case "cursor"->service.onUpdateSelection(0,0,1,1,-1,-1);case "selection"->service.onUpdateSelection(0,0,0,1,-1,-1);case "sameOffsets"->service.onUpdateSelection(0,0,0,0,-1,-1);case "composition"->service.onUpdateSelection(0,0,0,0,0,4);}}
        void change(String name){switch(name){case "connection"->service.connection=new Host();case "field"->service.info.fieldId++;case "package"->service.info.packageName="other.host";case "type"->service.info.inputType=129;case "hidden"->service.shown=false;case "locked"->service.keyguard.locked=true;}}
        void exit(String name)throws Exception{switch(name){case "keyboard"->click("↩ Keyboard");case "finishView"->service.onFinishInputView(false);case "finishInput"->service.onFinishInput();case "hidden"->service.onWindowHidden();case "lock"->service.receiver.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));case "destroy"->service.onDestroy();case "newEditor"->service.onStartInput(new EditorInfo(),false);case "recreate"->{root=(LinearLayout)service.onCreateInputView();status=(TextView)field(service,"status");}default->throw new AssertionError(name);}}
    }
    static final class Host implements InputConnection{
        String text="",lastCommit;int start,end,compStart=-1,compEnd=-1,commits,extractedReads,selectedReads,finishComposingCalls,partialStart=-1;
        boolean commitResult=true,throwCommit,deleteResult=true,nullExtraction,finishComposingResult=true,throwFinishComposing;
        Runnable onExtract,onCommit,onSelect,onDelete,onSelectedRead,onFinishComposing;
        public boolean finishComposingText(){finishComposingCalls++;if(onFinishComposing!=null)onFinishComposing.run();if(throwFinishComposing)throw new IllegalStateException("SYNTHETIC_UNSAFE_EXCEPTION");if(finishComposingResult)compStart=compEnd=-1;return finishComposingResult;}
        public ExtractedText getExtractedText(ExtractedTextRequest request,int flags){extractedReads++;ExtractedText e=new ExtractedText();e.text=text;e.selectionStart=start;e.selectionEnd=end;e.partialStartOffset=partialStart;if(onExtract!=null)onExtract.run();return nullExtraction?null:e;}
        public boolean beginBatchEdit(){return true;}public boolean endBatchEdit(){return true;}
        public boolean setSelection(int start,int end){this.start=start;this.end=end;if(onSelect!=null)onSelect.run();return true;}
        public boolean commitText(CharSequence value,int position){commits++;lastCommit=value.toString();if(onCommit!=null)onCommit.run();if(throwCommit)throw new IllegalStateException("SYNTHETIC_UNSAFE_EXCEPTION");if(commitResult){int from=compStart>=0?compStart:Math.min(start,end),to=compEnd>=0?compEnd:Math.max(start,end);text=text.substring(0,from)+value+text.substring(to);start=end=from+value.length();compStart=compEnd=-1;}return commitResult;}
        public CharSequence getSelectedText(int flags){selectedReads++;String result=text.substring(Math.min(start,end),Math.max(start,end));if(onSelectedRead!=null)onSelectedRead.run();return result;}
        public boolean deleteSurroundingTextInCodePoints(int before,int after){if(onDelete!=null)onDelete.run();return deleteResult;}
    }
}
