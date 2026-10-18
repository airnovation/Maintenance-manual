package com.airnovation.aivoicekeyboard;
import android.content.Context;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
/** Retains callbacks to deliberately deliver cancelled/stale completions. Never uses network. */
final class VoiceDraftTransport {
    interface Callback { void done(long token, String transcript, String draft, String error); }
    record Request(long token, File file, String previous, Callback callback) { }
    static VoiceDraftTransport latest;
    final List<Request> requests = new ArrayList<>();
    int cancelCalls;
    VoiceDraftTransport(Context context) { latest = this; }
    void cancel() { cancelCalls++; }
    void process(VoiceDraftSession session, long token, File file, String previous, Callback callback) {
        requests.add(new Request(token, file, previous, callback));
        if (file != null) file.delete();
    }
    void complete(int index, String transcript, String draft, String error) {
        Request request = requests.get(index);
        request.callback.done(request.token, transcript, draft, error);
    }
}
