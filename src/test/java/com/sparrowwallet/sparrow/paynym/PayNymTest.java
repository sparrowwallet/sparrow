package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A contact entry in a PayNym response whose payment code does not parse is omitted, rather than becoming a PayNym with no payment code that the contact
 * lists and search then dereference.
 */
public class PayNymTest {
    private static final String PAYMENT_CODE = "PM8TJTLJbPRGxSbc8EJi42Wrr6QbNSaSSVJ5Y3E4pbCYiTHUskHg13935Ubb7q8tx9GVbh2UuRnBc3WSyJHhUrw8KhprKnn9eDznYGieTzFcwQRya4GA";
    private static final String OTHER_PAYMENT_CODE = "PM8TJS2JxQ5ztXUpBBRnpTbcUXbUHy2T1abfrb3KkAAtMEGNbey4oumH7Hc578WgQJhPjBxteQ5GHHToTYHE3A1w6p7tU6KSoFmWBVbFGjKPisZDbP97";

    @Test
    public void parsesAValidPaymentCode() {
        Optional<PayNym> payNym = PayNym.fromString(PAYMENT_CODE, "id", "+name", true, Collections.emptyList(), Collections.emptyList());
        assertTrue(payNym.isPresent());
        assertEquals(PAYMENT_CODE, payNym.get().paymentCode().toString());
    }

    @Test
    public void omitsAnUnparseablePaymentCode() {
        assertTrue(PayNym.fromString("not-a-payment-code", "id", "+name", true, Collections.emptyList(), Collections.emptyList()).isEmpty());

        //Valid Base58 with a corrupted checksum
        String corrupted = PAYMENT_CODE.substring(0, PAYMENT_CODE.length() - 1) + (PAYMENT_CODE.endsWith("A") ? "B" : "A");
        assertTrue(PayNym.fromString(corrupted, "id", "+name", true, Collections.emptyList(), Collections.emptyList()).isEmpty());
    }

    @Test
    public void omitsAPaymentCodeWithAnInvalidKey() {
        //Parses and passes its checksum, but the key's x coordinate is not on the curve
        String invalidKey = "PM8TJWnHNrXcofmpzddV4aoaZMMN3C7QhbymGxWYBQVKjs1fochBZk1dZ7ZnoqMkg2YwtACR3eRMipK7KqvQ6cG9dyc2eLFuwsA8SSuUHxRERKBFNx5G";
        assertTrue(PayNym.fromString(invalidKey, "id", "+name", true, Collections.emptyList(), Collections.emptyList()).isEmpty());
    }

    @Test
    public void childWalletNameNotReusedForAnotherPaymentCode() throws Exception {
        Wallet masterWallet = PayNymServiceTest.getWallet();
        PaymentCode alice = new PaymentCode(PAYMENT_CODE);
        PaymentCode bob = new PaymentCode(OTHER_PAYMENT_CODE);
        masterWallet.addChildWallet(alice, ScriptType.P2WPKH, "+alice " + ScriptType.P2WPKH.getName());

        PayNym namedAlice = PayNym.fromString(OTHER_PAYMENT_CODE, "id", "+alice", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals(bob.toAbbreviatedString(), PayNym.getChildWalletNymName(masterWallet, bob, namedAlice));

        PayNym namedBob = PayNym.fromString(OTHER_PAYMENT_CODE, "id", "+bob", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals("+bob", PayNym.getChildWalletNymName(masterWallet, bob, namedBob));

        PayNym aliceAgain = PayNym.fromString(PAYMENT_CODE, "id", "+alice", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals("+alice", PayNym.getChildWalletNymName(masterWallet, alice, aliceAgain));
    }

    @Test
    public void removesControlCharactersFromName() {
        PayNym payNym = PayNym.fromString(PAYMENT_CODE, "id", " +name\u0000\u001f\u007f\u0085 ", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals("+name", payNym.nymName());

        PayNym formatted = PayNym.fromString(PAYMENT_CODE, "id", "\u202e+na\u200bme\ufeff\u2066", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals("+name", formatted.nymName());
    }

    @Test
    public void nameOfOnlyControlCharactersUsesPaymentCode() {
        PayNym payNym = PayNym.fromString(PAYMENT_CODE, "id", "\u0007\u0008", true, Collections.emptyList(), Collections.emptyList()).orElseThrow();
        assertEquals(payNym.paymentCode().toAbbreviatedString(), payNym.nymName());
    }
}
