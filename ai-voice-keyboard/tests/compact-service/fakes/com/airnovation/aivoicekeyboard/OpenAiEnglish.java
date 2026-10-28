package com.airnovation.aivoicekeyboard;
import java.util.*;
public class OpenAiEnglish {
public interface Callback { void done(String result,String error); }
record Request(String source,Callback callback,EnglishRequest handle) {}
static final List<Request> requests=new ArrayList<>();
static java.util.function.Consumer<Request> duringRewrite;
public static EnglishRequest rewrite(String key,String original,Callback callback) {
    Request request = new Request(original,callback,new EnglishRequest());
    requests.add(request);
    if (duringRewrite != null) duringRewrite.accept(request);
    return request.handle();
}
static void reset() { requests.clear(); duringRewrite = null; }
static void complete(int index,String result,String error) { requests.get(index).callback().done(result,error); }
}
