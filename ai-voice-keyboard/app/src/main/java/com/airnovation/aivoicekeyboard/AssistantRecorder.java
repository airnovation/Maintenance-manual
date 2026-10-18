package com.airnovation.aivoicekeyboard;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.File;

/** User-started recording only. Finishing returns a file; this class never uploads. */
final class AssistantRecorder {
    interface Finished { void done(File file, String error); }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaRecorder recorder;
    private File audioDirectory;
    private File file;
    private long started;
    private final Runnable limit;
    AssistantRecorder(Context context, Runnable onLimit) {
        this.context = context; limit = onLimit;
        try { audioDirectory = AssistantAudioCache.prepare(context.getCacheDir()); }
        catch (Exception ignored) { audioDirectory = null; }
    }
    boolean recording() { return recorder != null; }
    String start() {
        cancel();
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            return "请先在应用设置手动允许麦克风权限。";
        try {
            audioDirectory = AssistantAudioCache.prepare(context.getCacheDir());
            file = File.createTempFile("assistant_reply_", ".m4a", audioDirectory);
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(64000); recorder.setAudioSamplingRate(16000);
            recorder.setOutputFile(file.getAbsolutePath()); recorder.prepare(); recorder.start();
            started = SystemClock.elapsedRealtime();
            main.postDelayed(limit, 60000);
            return null;
        } catch (Exception ignored) { cancel(); return "麦克风启动失败，请检查权限或其他录音应用。"; }
    }
    void finish(Finished callback) {
        main.removeCallbacks(limit);
        if (recorder == null) { callback.done(null, "尚未录音。"); return; }
        boolean ok = false;
        try { recorder.stop(); ok = SystemClock.elapsedRealtime() - started >= 500; }
        catch (Exception ignored) { }
        finally { recorder.release(); recorder = null; }
        File finished = file; file = null;
        if (!ok) { if (finished != null) finished.delete(); callback.done(null, "录音太短或被中断，请重试。"); }
        else callback.done(finished, null);
    }
    void cancel() {
        main.removeCallbacks(limit);
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
        if (file != null) file.delete(); file = null;
    }
}
