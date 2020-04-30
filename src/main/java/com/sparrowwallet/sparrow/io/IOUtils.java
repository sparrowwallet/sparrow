package com.sparrowwallet.sparrow.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class IOUtils {
    public static FileType getFileType(File file) {
        try {
            String type = Files.probeContentType(file.toPath());
            if (type == null) {
                return FileType.BINARY;
            } else if (type.equals("application/json")) {
                return FileType.JSON;
            } else if (type.startsWith("text")) {
                return FileType.TEXT;
            }
        } catch (IOException e) {
            //ignore
        }

        return FileType.UNKNOWN;
    }
}
