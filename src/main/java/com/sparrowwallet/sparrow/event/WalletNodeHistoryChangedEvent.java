package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.net.ElectrumServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Used to notify that a wallet node (identified by it's script hash) has been updated on the blockchain.
 * Does not extend WalletChangedEvent as the wallet is not known when this is fired.
 */
public class WalletNodeHistoryChangedEvent {
    private final String scriptHash;
    private final String status;

    public WalletNodeHistoryChangedEvent(String scriptHash) {
        this.scriptHash = scriptHash;
        this.status = null;
    }

    public WalletNodeHistoryChangedEvent(String scriptHash, String status) {
        this.scriptHash = scriptHash;
        this.status = status;
    }

    public WalletNode getWalletNode(Wallet wallet) {
        WalletNode changedNode = getNode(wallet);
        if(changedNode != null) {
            return changedNode;
        }

        for(Wallet childWallet : wallet.getChildWallets()) {
            if(childWallet.isNested()) {
                changedNode = getNode(childWallet);
                if(changedNode != null) {
                    return changedNode;
                }
            }
        }

        Wallet notificationWallet = wallet.getNotificationWallet();
        if(notificationWallet != null) {
            WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);
            if(ElectrumServer.getScriptHash(notificationNode).equals(scriptHash)) {
                return notificationNode;
            }
        }

        return null;
    }

    private WalletNode getNode(Wallet wallet) {
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            WalletNode changedNode = getWalletNode(wallet, keyPurpose);
            if(changedNode != null) {
                return changedNode;
            }
        }

        return null;
    }

    private WalletNode getWalletNode(Wallet wallet, KeyPurpose keyPurpose) {
        WalletNode purposeNode = wallet.getNode(keyPurpose);
        for(WalletNode addressNode : new ArrayList<>(purposeNode.getChildren())) {
            if(ElectrumServer.getScriptHash(addressNode).equals(scriptHash)) {
                return addressNode;
            }
        }

        return null;
    }

    public String getScriptHash() {
        return scriptHash;
    }

    public String getStatus() {
        return status;
    }
}
