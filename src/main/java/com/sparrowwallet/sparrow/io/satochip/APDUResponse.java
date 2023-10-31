package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.Utils;

/**
 * ISO7816-4 APDU response.
 */
public class APDUResponse {
    public static final int SW_OK = 0x9000;
    public static final int SW_SECURITY_CONDITION_NOT_SATISFIED = 0x6982;
    public static final int SW_AUTHENTICATION_METHOD_BLOCKED = 0x6983;
    public static final int SW_CARD_LOCKED = 0x6283;
    public static final int SW_REFERENCED_DATA_NOT_FOUND = 0x6A88;
    public static final int SW_CONDITIONS_OF_USE_NOT_SATISFIED = 0x6985; // applet may be already installed
    public static final int SW_WRONG_PIN_MASK = 0x63C0;
    public static final String HEXES = "0123456789ABCDEF";

    private final byte[] apdu;
    private byte[] data;
    private int sw;
    private int sw1;
    private int sw2;

    /**
     * Creates an APDU object by parsing the raw response from the card.
     *
     * @param apdu the raw response from the card.
     */
    public APDUResponse(byte[] apdu) {
        if(apdu.length < 2) {
            throw new IllegalArgumentException("APDU response must be at least 2 bytes");
        }
        this.apdu = apdu;
        this.parse();
    }

    public APDUResponse(byte[] data, byte sw1, byte sw2) {
        byte[] apdu = new byte[data.length + 2];
        System.arraycopy(data, 0, apdu, 0, data.length);
        apdu[data.length] = sw1;
        apdu[data.length + 1] = sw2;
        this.apdu = apdu;
        this.parse();
    }


    /**
     * Parses the APDU response, separating the response data from SW.
     */
    private void parse() {
        int length = this.apdu.length;

        this.sw1 = this.apdu[length - 2] & 0xff;
        this.sw2 = this.apdu[length - 1] & 0xff;
        this.sw = (this.sw1 << 8) | this.sw2;

        this.data = new byte[length - 2];
        System.arraycopy(this.apdu, 0, this.data, 0, length - 2);
    }

    /**
     * Returns the data field of this APDU.
     *
     * @return the data field of this APDU
     */
    public byte[] getData() {
        return this.data;
    }

    /**
     * Returns the Status Word.
     *
     * @return the status word
     */
    public int getSw() {
        return this.sw;
    }

    /**
     * Returns the SW1 byte
     *
     * @return SW1
     */
    public int getSw1() {
        return this.sw1;
    }

    /**
     * Returns the SW2 byte
     *
     * @return SW2
     */
    public int getSw2() {
        return this.sw2;
    }

    /**
     * Returns the raw unparsed response.
     *
     * @return raw APDU data
     */
    public byte[] getBytes() {
        return this.apdu;
    }

    /**
     * Serializes the APDU to human readable hex string format
     *
     * @return the hex string representation of the APDU
     */
    public String toHexString() {
        byte[] raw = this.apdu;
        if(raw == null) {
            return "";
        }

        return Utils.bytesToHex(raw);
    }
}
