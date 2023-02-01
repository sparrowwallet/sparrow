package com.sparrowwallet.sparrow.io.ckcard;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.InvalidAddressException;

import javax.smartcardio.CardException;

public class CardDump extends CardResponse {
    int slot;
    boolean used = true;
    boolean sealed;
    String address;
    byte[] pubkey;

    public Address getAddress() throws CardException {
        try {
            if(address != null) {
                return Address.fromString(address);
            }
        } catch(InvalidAddressException e) {
            throw new CardException("Invalid address provided", e);
        }

        return null;
    }
}
