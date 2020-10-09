package com.sparrowwallet.sparrow.io;

import java.io.File;

public interface FileImport extends Import {
    boolean isEncrypted(File file);
    boolean isScannable();
}
