package com.sparrowwallet.sparrow.io;

import java.io.File;
import java.io.InputStream;

public class IoTest {
    public static final String IO_TEST_PATH = "/com/sparrowwallet/sparrow/io/";

    protected File getFile(String filename) {
        return new File(this.getClass().getResource(IO_TEST_PATH + filename).getFile());
    }

    protected InputStream getInputStream(String filename) {
        return this.getClass().getResourceAsStream(IO_TEST_PATH + filename);
    }
}
