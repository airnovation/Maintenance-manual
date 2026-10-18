package android.media;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Host-only recorder: writes invented bytes and never touches a microphone or network. */
public final class MediaRecorder {
    public static final class AudioSource { public static final int MIC = 1; }
    public static final class OutputFormat { public static final int MPEG_4 = 2; }
    public static final class AudioEncoder { public static final int AAC = 3; }
    public static final class Failures {
        public boolean prepare;
        public boolean start;
        public boolean stop;
        public boolean release;
    }

    public static Failures nextFailures = new Failures();
    public static final List<MediaRecorder> instances = new ArrayList<>();
    private final Failures failures;
    private boolean prepared;
    private boolean running;
    private boolean released;
    public File outputFile;
    public int prepareCalls;
    public int startCalls;
    public int stopCalls;
    public int releaseCalls;
    public int audioSource;
    public int outputFormat;
    public int audioEncoder;
    public int encodingBitRate;
    public int samplingRate;

    public MediaRecorder() {
        failures = nextFailures;
        nextFailures = new Failures();
        instances.add(this);
    }
    public static void reset() { instances.clear(); nextFailures = new Failures(); }
    public void setAudioSource(int value) { audioSource = value; }
    public void setOutputFormat(int value) { outputFormat = value; }
    public void setAudioEncoder(int value) { audioEncoder = value; }
    public void setAudioEncodingBitRate(int value) { encodingBitRate = value; }
    public void setAudioSamplingRate(int value) { samplingRate = value; }
    public void setOutputFile(String path) { outputFile = new File(path); }
    public void prepare() throws IOException {
        prepareCalls++;
        Files.write(outputFile.toPath(), new byte[] { 1, 2, 3, 4 });
        if (failures.prepare) throw new IOException("Injected prepare failure");
        prepared = true;
    }
    public void start() {
        startCalls++;
        if (failures.start) throw new IllegalStateException("Injected start failure");
        if (!prepared || released) throw new IllegalStateException("Not prepared");
        running = true;
    }
    public void stop() {
        stopCalls++;
        if (failures.stop) throw new RuntimeException("Injected stop failure");
        if (!running || released) throw new IllegalStateException("Not recording");
        running = false;
    }
    public void release() {
        releaseCalls++;
        if (failures.release) throw new IllegalStateException("Injected release failure");
        released = true;
        running = false;
    }
}
