package com.sparrowwallet.sparrow.io.keycard;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

/**
 * Tiny BER-TLV implementation. Not for general usage, but fast and easy to use for this project.
 */
public class TinyBERTLV {
    public static final byte TLV_BOOL = (byte) 0x01;
    public static final byte TLV_INT = (byte) 0x02;

    public static final int END_OF_TLV = (int) 0xffffffff;

    private byte[] buffer;
    private int pos;

    public static int[] readNum(byte[] buf, int off) {
        int len = buf[off++] & 0xff;
        int lenlen = 0;

        if((len & 0x80) == 0x80) {
            lenlen = len & 0x7f;
            len = readVal(buf, off, lenlen);
        }

        return new int[]{len, off + lenlen};
    }

    public static int readVal(byte[] val, int off, int len) {
        switch(len) {
            case 1:
                return val[off] & 0xff;
            case 2:
                return ((val[off] & 0xff) << 8) | (val[off + 1] & 0xff);
            case 3:
                return ((val[off] & 0xff) << 16) | ((val[off + 1] & 0xff) << 8) | (val[off + 2] & 0xff);
            case 4:
                return ((val[off] & 0xff) << 24) | ((val[off + 1] & 0xff) << 16) | ((val[off + 2] & 0xff) << 8) | (val[off + 3] & 0xff);
            default:
                throw new IllegalArgumentException("Integers of length " + len + " are unsupported");
        }
    }

    public static void writeNum(ByteArrayOutputStream os, int len) {
        if((len & 0xff000000) != 0) {
            os.write(0x84);
            os.write((len & 0xff000000) >> 24);
            os.write((len & 0x00ff0000) >> 16);
            os.write((len & 0x0000ff00) >> 8);
            os.write(len & 0x000000ff);
        } else if((len & 0x00ff0000) != 0) {
            os.write(0x83);
            os.write((len & 0x00ff0000) >> 16);
            os.write((len & 0x0000ff00) >> 8);
            os.write(len & 0x000000ff);
        } else if((len & 0x0000ff00) != 0) {
            os.write(0x82);
            os.write((len & 0x0000ff00) >> 8);
            os.write(len & 0x000000ff);
        } else if((len & 0x00000080) != 0) {
            os.write(0x81);
            os.write(len & 0x000000ff);
        } else {
            os.write(len);
        }
    }

    public TinyBERTLV(byte[] buffer) {
        this.buffer = buffer;
        this.pos = 0;
    }

    /**
     * Enters a constructed TLV with the given tag
     *
     * @param tag the tag to enter
     * @return the length of the TLV
     * @throws IllegalArgumentException if the next tag does not match the given one
     */
    public int enterConstructed(int tag) throws IllegalArgumentException {
        checkTag(tag, readTag());
        return readLength();
    }

    /**
     * Reads a primitive TLV with the given tag
     *
     * @param tag the tag to read
     * @return the body of the TLV
     * @throws IllegalArgumentException if the next tag does not match the given one
     */
    public byte[] readPrimitive(int tag) throws IllegalArgumentException {
        checkTag(tag, readTag());
        int len = readLength();
        pos += len;
        return Arrays.copyOfRange(buffer, (pos - len), pos);
    }

    /**
     * Reads a boolean TLV.
     *
     * @return the boolean value of the TLV
     * @throws IllegalArgumentException if the next tag is not a boolean
     */
    public boolean readBoolean() throws IllegalArgumentException {
        byte[] val = readPrimitive(TLV_BOOL);
        return ((val[0] & 0xff) == 0xff);
    }

    /**
     * Reads an integer TLV.
     *
     * @return the integer value of the TLV
     * @throws IllegalArgumentException if the next tlv is not an integer or is of unsupported length
     */
    public int readInt() throws IllegalArgumentException {
        byte[] val = readPrimitive(TLV_INT);
        return TinyBERTLV.readVal(val, 0, val.length);
    }

    /**
     * Returns all unread bytes in the TLV.
     *
     * @return all unread bytes
     */
    byte[] peekUnread() {
        return Arrays.copyOfRange(buffer, pos, buffer.length);
    }

    /**
     * Low-level method to unread the last read tag. Only valid if the previous call was readTag(). Does nothing if the
     * end of the TLV has been reached.
     */
    public void unreadLastTag() {
        if(pos < buffer.length) {
            pos--;
        }
    }

    /**
     * Reads the next tag. The current implementation only reads tags on one byte. Can be extended if needed.
     *
     * @return the tag
     */
    public int readTag() {
        return (pos < buffer.length) ? buffer[pos++] : END_OF_TLV;
    }

    /**
     * Reads the next tag. The current implementation only reads length on one and two bytes. Can be extended if needed.
     *
     * @return the tag
     */
    public int readLength() {
        int[] len = TinyBERTLV.readNum(buffer, pos);
        pos = len[1];
        return len[0];
    }

    private void checkTag(int expected, int actual) throws IllegalArgumentException {
        if(expected != actual) {
            unreadLastTag();
            throw new IllegalArgumentException("Expected tag: " + expected + ", received: " + actual);
        }
    }
}
