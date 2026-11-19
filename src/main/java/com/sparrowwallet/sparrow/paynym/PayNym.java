package com.sparrowwallet.sparrow.paynym;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.bip47.InvalidPaymentCodeException;
import com.sparrowwallet.drongo.bip47.PaymentCode;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    private Address notificationAddress;

    public PayNym(PaymentCode paymentCode, String nymId, String nymName, boolean segwit, List<PayNym> following, List<PayNym> followers) {
        this.paymentCode = paymentCode;
        this.nymId = nymId;
        String strippedName = nymName == null ? null : nymName.replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
        this.nymName = strippedName != null && strippedName.isEmpty() ? paymentCode.toAbbreviatedString() : strippedName;
        this.segwit = segwit;
        this.following = following;
        this.followers = followers;
    }

    public PaymentCode paymentCode() {
        return paymentCode;
    }

    public Address notificationAddress() {
        if(notificationAddress == null) {
            notificationAddress = paymentCode.getNotificationAddress();
        }

        return notificationAddress;
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
        return SEGWIT_SCRIPT_TYPES;
    }

    public static List<ScriptType> getV1ScriptTypes() {
        return V1_SCRIPT_TYPES;
    }

    public static Optional<PayNym> fromString(String strPaymentCode, String nymId, String nymName, boolean segwit, List<PayNym> following, List<PayNym> followers) {
        try {
            PayNym payNym = new PayNym(new PaymentCode(strPaymentCode), nymId, nymName, segwit, following, followers);
            payNym.notificationAddress();
            return Optional.of(payNym);
        } catch(InvalidPaymentCodeException | IllegalArgumentException e) {
            log.error("Error creating PayNym from payment code " + strPaymentCode, e);
            return Optional.empty();
        }
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

    public static String getChildWalletNymName(Wallet masterWallet, PaymentCode externalPaymentCode, PayNym payNym) {
        if(payNym == null || payNym.nymName() == null) {
            return externalPaymentCode.toAbbreviatedString();
        }

        //A name already given to a stored contact with another payment code would show two contacts under it
        Address notificationAddress = externalPaymentCode.getNotificationAddress();
        boolean nameInUse = masterWallet.getChildWallets().stream().filter(Wallet::isBip47).anyMatch(childWallet -> payNym.nymName().equals(getNymName(childWallet))
                && !childWallet.getKeystores().get(0).getExternalPaymentCode().getNotificationAddress().equals(notificationAddress));
        return nameInUse ? externalPaymentCode.toAbbreviatedString() : payNym.nymName();
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
