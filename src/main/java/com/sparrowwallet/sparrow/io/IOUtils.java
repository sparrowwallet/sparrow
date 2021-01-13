package com.sparrowwallet.sparrow.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class IOUtils {
    public static FileType getFileType(File file) {
        try {
            String type = Files.probeContentType(file.toPath());
            if(type == null) {
                if(file.getName().toLowerCase().endsWith("txn") || file.getName().toLowerCase().endsWith("psbt")) {
                    return FileType.TEXT;
                }

                if(file.exists()) {
                    try(BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
                        String line = br.readLine();
                        if(line.startsWith("01000000") || line.startsWith("cHNid")) {
                            return FileType.TEXT;
                        } else if(line.startsWith("{")) {
                            return FileType.JSON;
                        }
                    }
                }

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
