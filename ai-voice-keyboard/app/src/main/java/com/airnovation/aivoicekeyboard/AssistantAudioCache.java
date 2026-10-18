package com.airnovation.aivoicekeyboard;

import java.io.File;
import java.io.IOException;

/** Only owns direct assistant_reply_<digits>.m4a files in this app's private subdirectory. */
public final class AssistantAudioCache {
    private AssistantAudioCache() { }
    public static File prepare(File appCache) throws IOException {
        File directory = new File(appCache.getCanonicalFile(), "assistant-recordings");
        if (!directory.getCanonicalFile().equals(directory.getAbsoluteFile())) throw new IOException("Unexpected cache link");
        if (!directory.isDirectory() && !directory.mkdir()) throw new IOException("Cannot create audio cache");
        File[] files = directory.listFiles();
        if (files == null) throw new IOException("Cannot inspect audio cache");
        for (File file : files) {
            if (file.getName().matches("assistant_reply_[0-9]+\\.m4a") && file.isFile()
                    && file.getCanonicalFile().equals(file.getAbsoluteFile())) {
                if (!file.delete()) throw new IOException("Cannot remove stale recording");
            }
        }
        return directory;
    }
}
