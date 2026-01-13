package com.sparrowwallet.sparrow.io.keycard;

import com.sparrowwallet.sparrow.io.CardAuthorizationException;

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

    private byte[] apdu;
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
     * Returns true if the SW is 0x9000.
     *
     * @return true if the SW is 0x9000.
     */
    public boolean isOK() {
        return this.sw == SW_OK;
    }

    /**
     * Asserts that the SW is 0x9000. Throws an exception if it isn't
     *
     * @return this object, to simplify chaining
     * @throws APDUException if the SW is not 0x9000
     */
    public APDUResponse checkOK() throws APDUException {
        return this.checkSW(SW_OK);
    }

    /**
     * Asserts that the SW is contained in the given list. Throws an exception if it isn't.
     *
     * @param codes the list of SWs to match.
     * @return this object, to simplify chaining
     * @throws APDUException if the SW is not 0x9000
     */
    public APDUResponse checkSW(int... codes) throws APDUException {
        for(int code : codes) {
            if(this.sw == code) {
                return this;
            }
        }

        switch(this.sw) {
            case SW_SECURITY_CONDITION_NOT_SATISFIED:
                throw new APDUException(this.sw, "security condition not satisfied");
            case SW_AUTHENTICATION_METHOD_BLOCKED:
                throw new APDUException(this.sw, "authentication method blocked");
            default:
                throw new APDUException(this.sw, "Unexpected error SW");
        }
    }

    /**
     * Asserts that the SW is 0x9000. Throws an exception with the given message if it isn't
     *
     * @param message the error message
     * @return this object, to simplify chaining
     * @throws APDUException if the SW is not 0x9000
     */
    public APDUResponse checkOK(String message) throws APDUException {
        return checkSW(message, SW_OK);
    }

    /**
     * Asserts that the SW is contained in the given list. Throws an exception with the given message if it isn't.
     *
     * @param message the error message
     * @param codes   the list of SWs to match.
     * @return this object, to simplify chaining
     * @throws APDUException if the SW is not 0x9000
     */
    public APDUResponse checkSW(String message, int... codes) throws APDUException {
        for(int code : codes) {
            if(this.sw == code) {
                return this;
            }
        }

        throw new APDUException(this.sw, message);
    }

    /**
     * Checks response from an authentication command (VERIFY PIN, UNBLOCK PUK)
     *
     * @throws CardAuthorizationException wrong PIN
     * @throws APDUException              unexpected response
     */
    public APDUResponse checkAuthOK() throws CardAuthorizationException, APDUException {
        if((this.sw & SW_WRONG_PIN_MASK) == SW_WRONG_PIN_MASK) {
            int retryAttempts = sw2 & 0x0F;
            throw new CardAuthorizationException("Wrong PIN, remaining tries: " + retryAttempts);
        } else {
            return checkOK();
        }
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
}
