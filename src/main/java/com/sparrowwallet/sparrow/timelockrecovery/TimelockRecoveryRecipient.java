package com.sparrowwallet.sparrow.timelockrecovery;

import com.sparrowwallet.drongo.address.Address;

public record TimelockRecoveryRecipient(Address address, String label, long amountSats, boolean remaining) {
    public TimelockRecoveryRecipient {
        if(address == null) {
            throw new IllegalArgumentException("Recipient address is required");
        }
    }

    public static TimelockRecoveryRecipient remaining(Address address, String label) {
        return new TimelockRecoveryRecipient(address, label, 0, true);
    }

    public static TimelockRecoveryRecipient of(Address address, String label, long amountSats) {
        return new TimelockRecoveryRecipient(address, label, amountSats, false);
    }
}
