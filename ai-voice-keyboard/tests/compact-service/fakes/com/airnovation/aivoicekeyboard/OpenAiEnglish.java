package com.airnovation.aivoicekeyboard;
import java.util.*;
public class OpenAiEnglish {
public interface Callback { void done(String result,String error); }
record Request(String source,Callback callback) {}
static final List<Request> requests=new ArrayList<>();
public static void rewrite(String key,String original,Callback callback) { requests.add(new Request(original,callback)); }
static void complete(int index,String result,String error) { requests.get(index).callback().done(result,error); }
}
