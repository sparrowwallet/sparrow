package com.sparrowwallet.sparrow.external;

import java.io.InputStream;

public class ImportExportTest {

    protected InputStream getInputStream(String filename) {
        return this.getClass().getResourceAsStream("/com/sparrowwallet/sparrow/external/" + filename);
    }
}
