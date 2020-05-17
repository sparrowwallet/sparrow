package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.sparrowwallet.drongo.crypto.ECIESKeyCrypter;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptedData;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ECIESInputStream extends FilterInputStream {
    private boolean decrypted;
    private final ECKey decryptionKey;
    private final byte[] encryptionMagic;

    public ECIESInputStream(InputStream in, ECKey decryptionKey, byte[] encryptionMagic) {
        super(in);

        if(in == null || decryptionKey == null || encryptionMagic == null) {
            throw new NullPointerException();
        }

        this.decryptionKey = decryptionKey;
        this.encryptionMagic = encryptionMagic;
    }

    public ECIESInputStream(InputStream in, ECKey decryptionKey) {
        this(in, decryptionKey, "BIE1".getBytes(StandardCharsets.UTF_8));
    }

    public int read() throws IOException {
        ensureDecrypted();
        return super.read();
    }

    public int read(byte[] b, int off, int len) throws IOException {
        ensureDecrypted();
        return super.read(b, off, len);
    }

    private synchronized void ensureDecrypted() throws IOException {
        if(!decrypted) {
            byte[] encryptedBytes = ByteStreams.toByteArray(in);
            in.close();
            ECIESKeyCrypter keyCrypter = new ECIESKeyCrypter();
            byte[] decryptedBytes = keyCrypter.decrypt(new EncryptedData(encryptionMagic, encryptedBytes, null, null), decryptionKey);
            in = new ByteArrayInputStream(decryptedBytes);
            decrypted = true;
        }
    }
}
