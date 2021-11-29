package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.bip47.rpc.PaymentCode;

public record PayNym(PaymentCode paymentCode, String nymId, String nymName, boolean segwit) {}
