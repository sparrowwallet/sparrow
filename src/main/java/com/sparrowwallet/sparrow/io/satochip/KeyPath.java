package com.sparrowwallet.sparrow.io.satochip;

import java.util.StringTokenizer;

/**
 * Keypath object to be used with the SatochipCommandSet
 */
public class KeyPath {
    private final byte[] data;

    /**
     * Parses a keypath into a byte array to be used with the SatochipCommandSet object.
     * <p>
     * A valid string is composed of a minimum of one and a maximum of 11 components separated by "/".
     * <p>
     * The first component should be "m", indicating the master key.
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

        String sourceOrFirstElement = tokenizer.nextToken(); // m

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

    public KeyPath(byte[] data) {
        this.data = data;
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
        sb.append('m');

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
