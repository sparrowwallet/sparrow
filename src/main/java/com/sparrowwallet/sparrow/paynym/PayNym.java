package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

import static com.sparrowwallet.drongo.bip47.PaymentCode.SEGWIT_SCRIPT_TYPES;
import static com.sparrowwallet.drongo.bip47.PaymentCode.V1_SCRIPT_TYPES;

public class PayNym {
    private static final Logger log = LoggerFactory.getLogger(PayNym.class);

    private final PaymentCode paymentCode;
    private final String nymId;
    private final String nymName;
    private final boolean segwit;
    private final List<PayNym> following;
    private final List<PayNym> followers;

    private boolean collaborativeSend;

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

    public boolean isCollaborativeSend() {
        return collaborativeSend;
    }

    public void setCollaborativeSend(boolean collaborativeSend) {
        this.collaborativeSend = collaborativeSend;
    }

    public List<ScriptType> getScriptTypes() {
        return segwit ? getSegwitScriptTypes() : getV1ScriptTypes();
    }

    public static List<ScriptType> getSegwitScriptTypes() {
        return SEGWIT_SCRIPT_TYPES;
    }

    public static List<ScriptType> getV1ScriptTypes() {
        return V1_SCRIPT_TYPES;
    }

    public static PayNym fromString(String strPaymentCode, String nymId, String nymName, boolean segwit, List<PayNym> following, List<PayNym> followers) {
        PaymentCode paymentCode;
        try {
            paymentCode = new PaymentCode(strPaymentCode);
        } catch(InvalidPaymentCodeException e) {
            log.error("Error creating PayNym from payment code " + strPaymentCode, e);
            paymentCode = null;
        }

        return new PayNym(paymentCode, nymId, nymName, segwit, following, followers);
    }

    public static PayNym fromWallet(Wallet bip47Wallet) {
        if(!bip47Wallet.isBip47()) {
            throw new IllegalArgumentException("Not a BIP47 wallet");
        }

        PaymentCode externalPaymentCode = bip47Wallet.getKeystores().get(0).getExternalPaymentCode();
        String nymName = externalPaymentCode.toAbbreviatedString();
        String walletNymName = getNymName(bip47Wallet);
        if(walletNymName != null) {
            nymName = walletNymName;
        }

        boolean segwit = bip47Wallet.getScriptType() != ScriptType.P2PKH;
        return new PayNym(externalPaymentCode, null, nymName, segwit, Collections.emptyList(), Collections.emptyList());
    }

    public static String getNymName(Wallet bip47Wallet) {
        if(bip47Wallet.getLabel() != null) {
            String suffix = " " + bip47Wallet.getScriptType().getName();
            if(bip47Wallet.getLabel().endsWith(suffix)) {
                return bip47Wallet.getLabel().substring(0, bip47Wallet.getLabel().length() - suffix.length());
            }
        }

        return null;
    }
}
