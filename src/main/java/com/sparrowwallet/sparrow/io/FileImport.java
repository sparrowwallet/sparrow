package com.sparrowwallet.sparrow.io;

import java.io.File;

public interface FileImport extends ImportExport {
    boolean isEncrypted(File file);
}
