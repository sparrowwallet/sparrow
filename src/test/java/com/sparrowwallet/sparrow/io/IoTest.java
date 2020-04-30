package com.sparrowwallet.sparrow.io;

import java.io.InputStream;

public class IoTest {

    protected InputStream getInputStream(String filename) {
        return this.getClass().getResourceAsStream("/com/sparrowwallet/sparrow/io/" + filename);
    }
}
