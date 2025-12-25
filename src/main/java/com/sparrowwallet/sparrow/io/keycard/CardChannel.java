package com.sparrowwallet.sparrow.io.keycard;

import java.io.IOException;

/**
 * A channel to transcieve ISO7816-4 APDUs.
 */
public interface CardChannel {
    /**
     * Sends the given C-APDU and returns an R-APDU.
     *
     * @param cmd the command to send
     * @return the card response
     * @throws IOException communication error
     */
    APDUResponse send(APDUCommand cmd) throws IOException;

    /**
     * True if connected, false otherwise
     *
     * @return true if connected, false otherwise
     */
    boolean isConnected();

    /**
     * Returns the iteration count for deriving the pairing key from the pairing password. The default is 50000 and is
     * should only be changed for devices where the PBKDF2 is calculated on-board and the resource do not permit a
     * high iteration count. If a lower count is used other security mechanism should be used to prevent brute force
     * attacks.
     *
     * @return the iteration count
     */
    default int pairingPasswordPBKDF2IterationCount() {
        return 50000;
    }
}
