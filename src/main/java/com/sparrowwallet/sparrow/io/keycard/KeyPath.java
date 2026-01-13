package com.sparrowwallet.sparrow.io.keycard;

import java.util.StringTokenizer;

/**
 * Keypath object to be used with the KeycardCommandSet
 */
public class KeyPath {
    private int source;
    private byte[] data;

    /**
     * Parses a keypath into a byte array and source parameter to be used with the KeycardCommandSet object.
     * <p>
     * A valid string is composed of a minimum of one and a maximum of 11 components separated by "/".
     * <p>
     * The first component can be either "m", indicating the master key, "..", indicating the parent of the current key,
     * or "." indicating the current key. It can also be omitted, in which case it is considered the same as being ".".
     * <p>
     * All other components are positive integers fitting in 31 bit, eventually suffixed by an apostrophe (') sign,
     * which indicates an hardened key.
     * <p>
     * An example of a valid path is "m/44'/0'/0'/0/0"
     *
     * @param keypath the keypath as a string
     */
    public KeyPath(String keypath) {
        StringTokenizer tokenizer = new StringTokenizer(keypath, "/");

        String sourceOrFirstElement = tokenizer.nextToken();

        switch(sourceOrFirstElement) {
            case "m":
                source = KeycardCommandSet.DERIVE_P1_SOURCE_MASTER;
                break;
            case "..":
                source = KeycardCommandSet.DERIVE_P1_SOURCE_PARENT;
                break;
            case ".":
                source = KeycardCommandSet.DERIVE_P1_SOURCE_CURRENT;
                break;
            default:
                source = KeycardCommandSet.DERIVE_P1_SOURCE_CURRENT;
                tokenizer = new StringTokenizer(keypath, "/"); // rewind
                break;
        }

        int componentCount = tokenizer.countTokens();
        if(componentCount > 10) {
            throw new IllegalArgumentException("Too many components");
        }

        data = new byte[4 * componentCount];

        for(int i = 0; i < componentCount; i++) {
            long component = parseComponent(tokenizer.nextToken());
            writeComponent(component, i);
        }
    }

    public KeyPath(byte[] data, int source) {
        this.data = data;
        this.source = source;
    }

    public KeyPath(byte[] data) {
        this(data, KeycardCommandSet.DERIVE_P1_SOURCE_MASTER);
    }

    private long parseComponent(String num) {
        long sign;

        if(num.endsWith("'")) {
            sign = 0x80000000L;
            num = num.substring(0, (num.length() - 1));
        } else {
            sign = 0L;
        }

        if(num.startsWith("+") || num.startsWith("-")) {
            throw new NumberFormatException("No sign allowed");
        }
        return (sign | Long.parseLong(num));
    }

    private void writeComponent(long component, int i) {
        int off = (i * 4);
        data[off] = (byte) ((component >> 24) & 0xff);
        data[off + 1] = (byte) ((component >> 16) & 0xff);
        data[off + 2] = (byte) ((component >> 8) & 0xff);
        data[off + 3] = (byte) (component & 0xff);
    }

    /**
     * The source of the derive command.
     *
     * @return the source of the derive command
     */
    public int getSource() {
        return source;
    }

    /**
     * The byte encoded key path.
     *
     * @return byte encoded key path
     */
    public byte[] getData() {
        return data;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();

        switch(source) {
            case KeycardCommandSet.DERIVE_P1_SOURCE_MASTER:
                sb.append('m');
                break;
            case KeycardCommandSet.DERIVE_P1_SOURCE_PARENT:
                sb.append("..");
                break;
            case KeycardCommandSet.DERIVE_P1_SOURCE_CURRENT:
                sb.append('.');
                break;
        }

        for(int i = 0; i < this.data.length; i += 4) {
            sb.append('/');
            appendComponent(sb, i);
        }

        return sb.toString();
    }

    private void appendComponent(StringBuffer sb, int i) {
        int num = ((this.data[i] & 0x7f) << 24) | ((this.data[i + 1] & 0xff) << 16) | ((this.data[i + 2] & 0xff) << 8) | (this.data[i + 3] & 0xff);
        sb.append(num);

        if((this.data[i] & 0x80) == 0x80) {
            sb.append('\'');
        }
    }
}
