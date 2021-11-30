package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.bip47.rpc.PaymentCode;

import java.util.List;

public record PayNym(PaymentCode paymentCode, String nymId, String nymName, boolean segwit, List<PayNym> following, List<PayNym> followers) {}
