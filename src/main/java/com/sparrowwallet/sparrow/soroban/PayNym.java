package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.sparrowwallet.drongo.protocol.ScriptType;

import java.util.List;

public class PayNym {
    private final PaymentCode paymentCode;
    private final String nymId;
    private final String nymName;
    private final boolean segwit;
    private final List<PayNym> following;
    private final List<PayNym> followers;

    public PayNym(PaymentCode paymentCode, String nymId, String nymName, boolean segwit, List<PayNym> following, List<PayNym> followers) {
        this.paymentCode = paymentCode;
        this.nymId = nymId;
        this.nymName = nymName;
        this.segwit = segwit;
        this.following = following;
        this.followers = followers;
    }

    public PaymentCode paymentCode() {
        return paymentCode;
    }

    public String nymId() {
        return nymId;
    }

    public String nymName() {
        return nymName;
    }

    public boolean segwit() {
        return segwit;
    }

    public List<PayNym> following() {
        return following;
    }

    public List<PayNym> followers() {
        return followers;
    }

    public List<ScriptType> getScriptTypes() {
        return segwit ? getSegwitScriptTypes() : getV1ScriptTypes();
    }

    public static List<ScriptType> getSegwitScriptTypes() {
        return List.of(ScriptType.P2PKH, ScriptType.P2SH_P2WPKH, ScriptType.P2WPKH);
    }

    public static List<ScriptType> getV1ScriptTypes() {
        return List.of(ScriptType.P2PKH);
    }
}
