package com.sparrowwallet.sparrow.paynym;

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
}
