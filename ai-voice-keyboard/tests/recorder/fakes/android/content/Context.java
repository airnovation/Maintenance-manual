package android.content;

import android.Manifest;
import android.content.pm.PackageManager;
import java.io.File;

/** Host-only context; it cannot request permissions or access Android services. */
public final class Context {
    private final File cacheDirectory;
    public int microphonePermission = PackageManager.PERMISSION_GRANTED;
    public int permissionChecks;

    public Context(File cacheDirectory) { this.cacheDirectory = cacheDirectory; }
    public File getCacheDir() { return cacheDirectory; }
    public int checkSelfPermission(String permission) {
        if (!Manifest.permission.RECORD_AUDIO.equals(permission))
            throw new AssertionError("Unexpected permission: " + permission);
        permissionChecks++;
        return microphonePermission;
    }
}
