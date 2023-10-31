package com.sparrowwallet.sparrow.io.satochip;

import com.sparrowwallet.drongo.Utils;

import java.io.ByteArrayOutputStream;

/**
 * ISO7816-4 APDU.
 */
public class APDUCommand {
    protected int cla;
    protected int ins;
    protected int p1;
    protected int p2;
    protected byte[] data;
    protected boolean needsLE;

    /**
     * Constructs an APDU with no response data length field. The data field cannot be null, but can be a zero-length array.
     *
     * @param cla  class byte
     * @param ins  instruction code
     * @param p1   P1 parameter
     * @param p2   P2 parameter
     * @param data the APDU data
     */
    public APDUCommand(int cla, int ins, int p1, int p2, byte[] data) {
        this(cla, ins, p1, p2, data, false);
    }

    /**
     * Constructs an APDU with an optional data length field. The data field cannot be null, but can be a zero-length array.
     * The LE byte, if sent, is set to 0.
     *
     * @param cla     class byte
     * @param ins     instruction code
     * @param p1      P1 parameter
     * @param p2      P2 parameter
     * @param data    the APDU data
     * @param needsLE whether the LE byte should be sent or not
     */
    public APDUCommand(int cla, int ins, int p1, int p2, byte[] data, boolean needsLE) {
        this.cla = cla & 0xff;
        this.ins = ins & 0xff;
        this.p1 = p1 & 0xff;
        this.p2 = p2 & 0xff;
        this.data = data;
        this.needsLE = needsLE;
    }

    /**
     * Serializes the APDU in order to send it to the card.
     *
     * @return the byte array representation of the APDU
     */
    public byte[] serialize() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(this.cla);
        out.write(this.ins);
        out.write(this.p1);
        out.write(this.p2);
        out.write(this.data.length);
        out.write(this.data, 0, this.data.length);

        if(this.needsLE) {
            out.write(0); // Response length
        }

        return out.toByteArray();
    }

    /**
     * Serializes the APDU to human readable hex string format
     *
     * @return the hex string representation of the APDU
     */
    public String toHexString() {
        byte[] raw = this.serialize();
        if(raw == null) {
            return "";
        }

        return Utils.bytesToHex(raw);
    }

    /**
     * Returns the CLA of the APDU
     *
     * @return the CLA of the APDU
     */
    public int getCla() {
        return cla;
    }

    /**
     * Returns the INS of the APDU
     *
     * @return the INS of the APDU
     */
    public int getIns() {
        return ins;
    }

    /**
     * Returns the P1 of the APDU
     *
     * @return the P1 of the APDU
     */
    public int getP1() {
        return p1;
    }

    /**
     * Returns the P2 of the APDU
     *
     * @return the P2 of the APDU
     */
    public int getP2() {
        return p2;
    }

    /**
     * Returns the data field of the APDU
     *
     * @return the data field of the APDU
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Returns whether LE is sent or not.
     *
     * @return whether LE is sent or not
     */
    public boolean getNeedsLE() {
        return this.needsLE;
    }
}
