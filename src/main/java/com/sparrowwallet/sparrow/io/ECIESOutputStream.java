package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.sparrowwallet.drongo.crypto.ECKey;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ECIESOutputStream extends FilterOutputStream {
    private final ECKey encryptionKey;
    private final byte[] encryptionMagic;
    private final OutputStream encryptedStream;

    public ECIESOutputStream(OutputStream out, ECKey encryptionKey, byte[] encryptionMagic) {
        super(new ByteArrayOutputStream());

        encryptedStream = out;

        if(encryptionKey == null || encryptionMagic == null) {
            throw new NullPointerException();
        }

        this.encryptionKey = encryptionKey;
        this.encryptionMagic = encryptionMagic;
    }

    public ECIESOutputStream(OutputStream in, ECKey encryptionKey) {
        this(in, encryptionKey, "BIE1".getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        super.close();
        byte[] unencrypted = ((ByteArrayOutputStream)out).toByteArray();
        byte[] encryptedWallet = encryptionKey.encryptEcies(unencrypted, encryptionMagic);
        ByteStreams.copy(new ByteArrayInputStream(encryptedWallet), encryptedStream);
        encryptedStream.flush();
        encryptedStream.close();
    }
}
