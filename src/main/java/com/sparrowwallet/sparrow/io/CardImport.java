package com.sparrowwallet.sparrow.io;

import javafx.beans.property.StringProperty;

import javax.smartcardio.CardException;

public interface CardImport extends ImportExport {
    boolean isInitialized() throws CardException;
    void initialize(String pin, byte[] chainCode, StringProperty messageProperty) throws CardException;
}
