package com.airnovation.aivoicekeyboard;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Handler;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Executes the production recorder/cache classes against isolated host-only Android fakes. */
public final class AssistantRecorderTest {
    private static int scenarios;
    private static int failures;
    private static int assertions;

    @FunctionalInterface private interface Test { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        test("finish without recording", AssistantRecorderTest::finishWithoutRecording);
        test("permission denied", AssistantRecorderTest::permissionDenied);
        test("permission revoked before restart", AssistantRecorderTest::permissionRevoked);
        test("unusable cache", AssistantRecorderTest::unusableCache);
        for (boolean prepare : new boolean[] { true, false }) {
            for (boolean release : new boolean[] { false, true }) {
                test((prepare ? "prepare" : "start") + " failure; release failure=" + release,
                        () -> startFailure(prepare, release));
            }
        }
        test("stop failure on finish", () -> finishFailure(true, false));
        test("release failure on finish", () -> finishFailure(false, true));
        test("stop and release failure on finish", () -> finishFailure(true, true));
        test("zero-duration recording", () -> shortRecording(0));
        test("499ms recording", () -> shortRecording(499));
        test("minimum 500ms recording", () -> successfulRecording(500));
        test("longer recording", () -> successfulRecording(5000));
        for (boolean stop : new boolean[] { false, true }) {
            for (boolean release : new boolean[] { false, true }) {
                test("cancel; stop failure=" + stop + "; release failure=" + release,
                        () -> cancellation(stop, release));
            }
        }
        test("60-second limit cancels once", AssistantRecorderTest::limitCancels);
        test("restarting removes old limits", AssistantRecorderTest::restartDeduplicatesLimit);
        test("finish clears pending limit", AssistantRecorderTest::finishClearsLimit);
        test("cancel clears pending limit", AssistantRecorderTest::cancelClearsLimit);
        test("finish callback may start a new recording", AssistantRecorderTest::callbackCanRestart);
        System.out.println("Recorder tests: " + (scenarios - failures) + "/" + scenarios
                + " scenarios passed; " + assertions + " assertions");
        if (failures != 0) throw new AssertionError(failures + " recorder scenario(s) failed");
    }

    private static void test(String name, Test test) {
        scenarios++;
        try {
            test.run();
            System.out.println("PASS " + name);
        } catch (Throwable failure) {
            failures++;
            System.err.println("FAIL " + name + ": " + failure);
            failure.printStackTrace(System.err);
        }
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void finishWithoutRecording() throws Exception {
        try (Fixture f = new Fixture()) {
            Result result = new Result();
            f.recorder.finish(result);
            result.error();
            check(MediaRecorder.instances.isEmpty(), "No recorder should be allocated");
            f.clean();
        }
    }

    private static void permissionDenied() throws Exception {
        try (Fixture f = new Fixture()) {
            f.context.microphonePermission = PackageManager.PERMISSION_DENIED;
            check(f.recorder.start() != null, "Permission denial must return an error");
            check(f.context.permissionChecks == 1, "Microphone permission must be checked");
            check(MediaRecorder.instances.isEmpty(), "Denied permission cannot allocate a recorder");
            f.clean();
            Handler.advanceBy(60000);
            check(f.limits == 0, "Denied permission cannot schedule a limit");
        }
    }

    private static void permissionRevoked() throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder previous = f.start();
            f.context.microphonePermission = PackageManager.PERMISSION_DENIED;
            check(f.recorder.start() != null, "A restart must recheck permission");
            check(previous.stopCalls == 1 && previous.releaseCalls == 1, "Old recorder must close");
            check(MediaRecorder.instances.size() == 1, "Denied restart cannot allocate a new recorder");
            check(!previous.outputFile.exists(), "Old recording must be deleted");
            f.clean();
        }
    }

    private static void unusableCache() throws Exception {
        Handler.reset();
        MediaRecorder.reset();
        Path root = Files.createTempDirectory("assistant-recorder-cache-test-");
        try {
            Path blocked = root.resolve("not-a-directory");
            Files.writeString(blocked, "keep");
            AssistantRecorder recorder = new AssistantRecorder(new Context(blocked.toFile()),
                    () -> { throw new AssertionError("Unexpected limit"); });
            check(recorder.start() != null, "Unusable cache must return an error");
            check(!recorder.recording(), "Unusable cache cannot enter recording state");
            check(MediaRecorder.instances.isEmpty(), "Unusable cache cannot allocate a recorder");
            check(Handler.pendingCount() == 0, "Unusable cache cannot schedule a limit");
            check(Files.readString(blocked).equals("keep"), "Unrelated file must be preserved");
        } finally { deleteTree(root); }
    }

    private static void startFailure(boolean prepare, boolean release) throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder.nextFailures.prepare = prepare;
            MediaRecorder.nextFailures.start = !prepare;
            MediaRecorder.nextFailures.release = release;
            check(f.recorder.start() != null, "Startup failure must return an error");
            MediaRecorder media = MediaRecorder.instances.get(0);
            check(media.prepareCalls == 1, "Prepare should be attempted once");
            check(media.startCalls == (prepare ? 0 : 1), "Start cannot run after prepare failure");
            check(media.releaseCalls == 1, "Failed startup must attempt release once");
            check(!media.outputFile.exists(), "Failed startup must delete even partially written audio");
            f.clean();
            f.start();
            f.recorder.cancel();
            f.clean();
        }
    }

    private static void finishFailure(boolean stop, boolean release) throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder.nextFailures.stop = stop;
            MediaRecorder.nextFailures.release = release;
            MediaRecorder media = f.start();
            Handler.advanceBy(1000);
            Result result = new Result();
            f.recorder.finish(result);
            result.error();
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Stop and release each run once");
            check(!media.outputFile.exists(), "Any finish failure must remove recorded audio");
            f.clean();
            f.recorder.cancel();
            Result second = new Result();
            f.recorder.finish(second);
            second.error();
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Failure must detach the old recorder");
            f.start();
            f.recorder.cancel();
            f.clean();
        }
    }

    private static void shortRecording(long duration) throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder media = f.start();
            Handler.advanceBy(duration);
            Result result = new Result();
            f.recorder.finish(result);
            result.error();
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Short recording must close");
            check(!media.outputFile.exists(), "Short recording must be deleted");
            f.clean();
        }
    }

    private static void successfulRecording(long duration) throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder media = f.start();
            check(media.audioSource == MediaRecorder.AudioSource.MIC, "Expected microphone source");
            check(media.outputFormat == MediaRecorder.OutputFormat.MPEG_4, "Expected MPEG4 container");
            check(media.audioEncoder == MediaRecorder.AudioEncoder.AAC, "Expected AAC encoder");
            check(media.encodingBitRate == 64000 && media.samplingRate == 16000, "Expected audio settings");
            Handler.advanceBy(duration);
            Result result = new Result();
            f.recorder.finish(result);
            result.success(media.outputFile);
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Successful recording must close");
            check(!f.recorder.recording(), "State must be idle before completion");
            check(Handler.pendingCount() == 0, "Success must clear the limit");
            f.recorder.cancel();
            check(result.file.exists(), "Cancel cannot delete a file already handed to the callback");
            Files.delete(result.file.toPath());
            f.clean();
        }
    }

    private static void cancellation(boolean stop, boolean release) throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder.nextFailures.stop = stop;
            MediaRecorder.nextFailures.release = release;
            MediaRecorder media = f.start();
            f.recorder.cancel();
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Cancel must attempt both stop and release");
            check(!media.outputFile.exists(), "Cancel must delete recorded audio");
            f.clean();
            f.recorder.cancel();
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Repeated cancel must be idempotent");
            f.clean();
        }
    }

    private static void limitCancels() throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder media = f.start();
            Handler.advanceBy(59999);
            check(f.limits == 0 && f.recorder.recording(), "Limit must not fire early");
            Handler.advanceBy(1);
            check(f.limits == 1, "Limit should fire once at 60 seconds");
            check(media.stopCalls == 1 && media.releaseCalls == 1, "Limit callback must cancel recorder");
            f.clean();
            Handler.advanceBy(120000);
            check(f.limits == 1, "Limit cannot repeat after cancellation");
        }
    }

    private static void restartDeduplicatesLimit() throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder first = f.start();
            Handler.advanceBy(10000);
            MediaRecorder second = f.start();
            Handler.advanceBy(10000);
            MediaRecorder third = f.start();
            check(first.stopCalls == 1 && first.releaseCalls == 1, "First recording must close");
            check(second.stopCalls == 1 && second.releaseCalls == 1, "Second recording must close");
            check(!first.outputFile.exists() && !second.outputFile.exists(), "Restart must delete old audio");
            check(f.recordings().size() == 1, "Only the current audio file may remain");
            check(Handler.pendingCount() == 1, "Restart must keep exactly one pending limit");
            Handler.advanceBy(59999);
            check(f.limits == 0 && f.recorder.recording(), "Old deadlines cannot cancel a new recording");
            Handler.advanceBy(1);
            check(f.limits == 1 && third.releaseCalls == 1, "Only the current limit may fire");
            f.clean();
        }
    }

    private static void finishClearsLimit() throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder media = f.start();
            Handler.advanceBy(1000);
            Result result = new Result();
            f.recorder.finish(result);
            result.success(media.outputFile);
            Handler.advanceBy(120000);
            check(f.limits == 0 && result.calls == 1, "No stale limit or duplicate completion after finish");
            Files.delete(result.file.toPath());
            f.clean();
        }
    }

    private static void cancelClearsLimit() throws Exception {
        try (Fixture f = new Fixture()) {
            f.start();
            f.recorder.cancel();
            Handler.advanceBy(120000);
            check(f.limits == 0, "Canceled recording cannot emit a stale limit");
            f.clean();
        }
    }

    private static void callbackCanRestart() throws Exception {
        try (Fixture f = new Fixture()) {
            MediaRecorder first = f.start();
            Handler.advanceBy(1000);
            int[] completions = { 0 };
            f.recorder.finish((file, error) -> {
                completions[0]++;
                check(!f.recorder.recording(), "Callback must observe detached recording state");
                check(error == null && file.equals(first.outputFile), "Callback must receive successful audio");
                check(file.delete(), "Callback owns and may consume its file");
                check(f.recorder.start() == null, "Callback may start another recording");
            });
            check(completions[0] == 1 && f.recorder.recording(), "Reentrant start must remain active");
            check(Handler.pendingCount() == 1, "Reentrant start must retain its own limit");
            f.recorder.cancel();
            f.clean();
        }
    }

    private static final class Result implements AssistantRecorder.Finished {
        int calls;
        File file;
        String message;
        @Override public void done(File file, String error) {
            calls++;
            this.file = file;
            message = error;
        }
        void error() {
            check(calls == 1, "Error must complete exactly once");
            check(file == null, "Error cannot hand out an audio file");
            check(message != null && !message.isEmpty(), "Error must explain the failure");
        }
        void success(File expected) {
            check(calls == 1, "Success must complete exactly once");
            check(message == null, "Success cannot have an error");
            check(expected.equals(file) && file.isFile() && file.length() > 0,
                    "Success must hand out the completed file");
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Path root;
        final Context context;
        final AssistantRecorder recorder;
        final Path sentinel;
        int limits;
        Fixture() throws Exception {
            Handler.reset();
            MediaRecorder.reset();
            root = Files.createTempDirectory("assistant-recorder-test-");
            context = new Context(root.toFile());
            recorder = new AssistantRecorder(context, this::onLimit);
            sentinel = root.resolve("assistant-recordings").resolve("unrelated.txt");
            Files.writeString(sentinel, "preserve");
        }
        private void onLimit() { limits++; recorder.cancel(); }
        MediaRecorder start() {
            check(recorder.start() == null, "Recording should start");
            check(recorder.recording(), "Successful start must report recording");
            check(Handler.pendingCount() == 1, "Successful start must schedule one limit");
            MediaRecorder media = MediaRecorder.instances.get(MediaRecorder.instances.size() - 1);
            check(media.outputFile.isFile(), "Recording must have a private temporary file");
            check(media.outputFile.getParentFile().equals(sentinel.getParent().toFile()),
                    "Recording must stay in its private cache subdirectory");
            return media;
        }
        List<Path> recordings() throws Exception {
            try (Stream<Path> entries = Files.list(sentinel.getParent())) {
                return entries.filter(path -> path.getFileName().toString().startsWith("assistant_reply_"))
                        .toList();
            }
        }
        void clean() throws Exception {
            check(!recorder.recording(), "Recorder state must be idle");
            check(Handler.pendingCount() == 0, "No limit may remain queued");
            check(recordings().isEmpty(), "No owned temporary audio may remain");
            check(Files.readString(sentinel).equals("preserve"), "Unrelated cache file must survive");
        }
        @Override public void close() throws Exception {
            try { recorder.cancel(); }
            finally { Handler.reset(); deleteTree(root); }
        }
    }

    private static void deleteTree(Path root) throws Exception {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
