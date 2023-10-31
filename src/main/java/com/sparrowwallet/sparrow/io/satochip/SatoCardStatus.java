package com.sparrowwallet.sparrow.io.satochip;

/**
 * Parses the result of a GET STATUS command retrieving application status.
 */
public class SatoCardStatus {
    private boolean setup_done = false;
    private boolean is_seeded = false;
    private boolean needs_secure_channel = false;
    private boolean needs_2FA = false;

    private byte protocol_major_version = (byte) 0;
    private byte protocol_minor_version = (byte) 0;
    private byte applet_major_version = (byte) 0;
    private byte applet_minor_version = (byte) 0;

    private byte PIN0_remaining_tries = (byte) 0;
    private byte PUK0_remaining_tries = (byte) 0;
    private byte PIN1_remaining_tries = (byte) 0;
    private byte PUK1_remaining_tries = (byte) 0;

    private int protocol_version = 0; //(d["protocol_major_version"]<<8)+d["protocol_minor_version"]

    /**
     * Constructor from TLV data
     *
     * @param rapdu the TLV data
     * @throws IllegalArgumentException if the TLV does not follow the expected format
     */
    public SatoCardStatus(APDUResponse rapdu) {
        int sw = rapdu.getSw();

        if(sw == 0x9000) {
            byte[] data = rapdu.getData();
            protocol_major_version = data[0];
            protocol_minor_version = data[1];
            applet_major_version = data[2];
            applet_minor_version = data[3];
            protocol_version = (protocol_major_version << 8) + protocol_minor_version;

            if(data.length >= 8) {
                PIN0_remaining_tries = data[4];
                PUK0_remaining_tries = data[5];
                PIN1_remaining_tries = data[6];
                PUK1_remaining_tries = data[7];
                needs_2FA = false; //default value
            }
            if(data.length >= 9) {
                needs_2FA = data[8] != 0X00;
            }
            if(data.length >= 10) {
                is_seeded = data[9] != 0X00;
            }
            if(data.length >= 11) {
                setup_done = data[10] != 0X00;
            } else {
                setup_done = true;
            }
            if(data.length >= 12) {
                needs_secure_channel = data[11] != 0X00;
            } else {
                needs_secure_channel = false;
                needs_2FA = false; //default value
            }
        } else if(sw == 0x9c04) {
            setup_done = false;
            is_seeded = false;
            needs_secure_channel = false;
        } else {
            throw new IllegalArgumentException("Invalid getStatus data");
        }
    }

    // getters
    public boolean isSeeded() {
        return is_seeded;
    }

    public boolean isSetupDone() {
        return setup_done;
    }

    public boolean isInitialized() {
        return (setup_done && is_seeded);
    }

    public boolean needsSecureChannel() {
        return needs_secure_channel;
    }

    public boolean needs2FA() {
        return needs_2FA;
    }

    public byte getPin0RemainingCounter() {
        return PIN0_remaining_tries;
    }

    public byte[] getCardVersion() {
        byte[] versionBytes = new byte[4];
        versionBytes[0] = protocol_major_version;
        versionBytes[1] = protocol_minor_version;
        versionBytes[2] = applet_major_version;
        versionBytes[3] = applet_minor_version;
        return versionBytes;
    }

    @Override
    public String toString() {
        return "setup_done: " + setup_done + "\n" +
                "is_seeded: " + is_seeded + "\n" +
                "needs_2FA: " + needs_2FA + "\n" +
                "needs_secure_channel: " + needs_secure_channel + "\n" +
                "protocol_major_version: " + protocol_major_version + "\n" +
                "protocol_minor_version: " + protocol_minor_version + "\n" +
                "applet_major_version: " + applet_major_version + "\n" +
                "applet_minor_version: " + applet_minor_version;
    }
}
