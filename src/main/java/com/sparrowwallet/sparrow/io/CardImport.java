package com.sparrowwallet.sparrow.io;

import javax.smartcardio.CardException;

public interface CardImport extends ImportExport {
    boolean isInitialized() throws CardException;
    void initialize(byte[] chainCode) throws CardException;
}
