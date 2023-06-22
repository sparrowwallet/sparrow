package com.sparrowwallet.sparrow.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class IoTest {
    public static final String IO_TEST_PATH = "/com/sparrowwallet/sparrow/io/";

    protected File getFile(String filename) {
        try {
            Path tempDir = Files.createTempDirectory(null);
            Path tempFile = Files.createTempFile(tempDir, filename, null);
            Files.copy(getInputStream(filename), tempFile, StandardCopyOption.REPLACE_EXISTING);
            return tempFile.toFile();
        } catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }

    protected InputStream getInputStream(String filename) {
        return this.getClass().getResourceAsStream(IO_TEST_PATH + filename);
    }
}
