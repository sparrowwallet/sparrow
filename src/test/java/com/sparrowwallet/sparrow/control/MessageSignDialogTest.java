package com.sparrowwallet.sparrow.control;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MessageSignDialogTest {
    @Test
    public void multisigAddressGivesSpecificValidationMessage() {
        // P2WSH (native witness multisig) address — script type is not allowed for single-sig
        String p2wshMainnet = "bc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qccfmv3";
        Assertions.assertEquals("Multisig addresses are not supported for signing",
                MessageSignDialog.getAddressValidationMessage(p2wshMainnet));
    }

    @Test
    public void unparseableAddressGivesGenericMessage() {
        Assertions.assertEquals("Invalid address",
                MessageSignDialog.getAddressValidationMessage("not-a-bitcoin-address"));
    }

    @Test
    public void emptyAddressGivesGenericMessage() {
        Assertions.assertEquals("Invalid address",
                MessageSignDialog.getAddressValidationMessage(""));
    }
}
