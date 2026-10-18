package com.airnovation.aivoicekeyboard;
public class VoiceEditorBindingTest {
    static int checks;
    static void check(boolean v){checks++;if(!v)throw new AssertionError(checks);}
    public static void main(String[] args){
        VoiceEditorBinding b=new VoiceEditorBinding();Object c=new Object();
        check(!b.matches(1,"app",1,1,c));b.bind(1,"app",1,1,c,true);
        check(b.matches(1,"app",1,1,c));check(!b.matches(2,"app",1,1,c));
        check(!b.matches(1,"other",1,1,c));check(!b.matches(1,"app",2,1,c));
        check(!b.matches(1,"app",1,2,c));check(!b.matches(1,"app",1,1,new Object()));
        check(!b.matches(1,"app",1,1,null));b.bind(1,"app",1,1,c,false);check(!b.matches(1,"app",1,1,c));
        b.bind(2,"app",1,1,c,true);check(b.matches(2,"app",1,1,c));check(!b.matches(1,"app",1,1,c));
        b.bind(2,"",1,1,c,true);check(!b.matches(2,"",1,1,c));
        b.bind(2,null,1,1,c,true);check(!b.matches(2,null,1,1,c));
        b.bind(2,"app",1,1,null,true);check(!b.matches(2,"app",1,1,null));
        System.out.println("PASS "+checks+" voice editor lifecycle/identity checks");
    }
}
