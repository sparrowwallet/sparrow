package com.sparrowwallet.sparrow.io.db;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class DbPersistenceHeaderTest {
    private static final byte[] MAGIC_1 = "SPRW1\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MAGIC_2 = "SPRW2\n".getBytes(StandardCharsets.UTF_8);
    private static final byte FLAG_CHALLENGE_RESPONSE = 0x01;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final File WALLET_FILE = new File("test.db");

    private static byte[] salt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        for(int i = 0; i < salt.length; i++) {
            salt[i] = (byte)(0xf0 + i);
        }
        return salt;
    }

    //Mirrors the layout written by writeBinaryHeader, from the wallet header magic onwards
    private static byte[] header(boolean challengeResponse, byte[] salt) {
        byte[] magic = challengeResponse ? MAGIC_2 : MAGIC_1;
        byte[] out = new byte[magic.length + (challengeResponse ? 1 : 0) + salt.length];
        System.arraycopy(magic, 0, out, 0, magic.length);
        int offset = magic.length;
        if(challengeResponse) {
            out[offset++] = FLAG_CHALLENGE_RESPONSE;
        }
        System.arraycopy(salt, 0, out, offset, salt.length);
        return out;
    }

    private static boolean readFlag(byte[] bytes, byte[] saltOut) throws IOException {
        DbPersistence persistence = new DbPersistence();
        try(InputStream inputStream = new ByteArrayInputStream(bytes)) {
            boolean flag = persistence.readChallengeResponseFlag(inputStream, WALLET_FILE);
            if(saltOut != null) {
                Assertions.assertEquals(saltOut.length, inputStream.readNBytes(saltOut, 0, saltOut.length));
            }
            return flag;
        }
    }

    @Test
    public void sprw1HeaderReportsNoChallengeResponseAndKeepsSaltOffset() throws IOException {
        byte[] salt = salt();
        byte[] read = new byte[SALT_LENGTH_BYTES];

        Assertions.assertFalse(readFlag(header(false, salt), read));
        Assertions.assertArrayEquals(salt, read);
    }

    @Test
    public void sprw2HeaderReportsChallengeResponseAndKeepsSaltOffset() throws IOException {
        byte[] salt = salt();
        byte[] read = new byte[SALT_LENGTH_BYTES];

        Assertions.assertTrue(readFlag(header(true, salt), read));
        Assertions.assertArrayEquals(salt, read);
    }

    @Test
    public void sprw2HeaderWithClearedFlagReportsNoChallengeResponse() throws IOException {
        byte[] salt = salt();
        byte[] bytes = header(true, salt);
        bytes[MAGIC_2.length] = 0x00;

        byte[] read = new byte[SALT_LENGTH_BYTES];
        Assertions.assertFalse(readFlag(bytes, read));
        Assertions.assertArrayEquals(salt, read);
    }

    @Test
    public void unknownFlagBitsAreRejectedRatherThanIgnored() {
        //A later version may add a flag that changes how the wallet must be opened, so it must not be silently dropped
        byte[] bytes = header(true, salt());
        bytes[MAGIC_2.length] = 0x02;

        Assertions.assertThrows(IOException.class, () -> readFlag(bytes, null));
    }

    @Test
    public void unknownFlagBitsAlongsideChallengeResponseAreAlsoRejected() {
        byte[] bytes = header(true, salt());
        bytes[MAGIC_2.length] = (byte)0x03;

        Assertions.assertThrows(IOException.class, () -> readFlag(bytes, null));
    }

    @Test
    public void truncatedSprw2HeaderIsRejectedRatherThanReadAsEnabled() {
        //A file ending right after the magic must not be read as challenge-response enabled
        Assertions.assertThrows(EOFException.class, () -> readFlag(MAGIC_2.clone(), null));
    }

    @Test
    public void truncatedMagicIsTreatedAsLegacyHeader() throws IOException {
        Assertions.assertFalse(readFlag(new byte[] {'S', 'P', 'R'}, null));
    }

    @Test
    public void emptyHeaderIsTreatedAsLegacyHeader() throws IOException {
        Assertions.assertFalse(readFlag(new byte[0], null));
    }
}
