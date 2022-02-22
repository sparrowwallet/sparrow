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

    public WalletNodeHistoryChangedEvent(String scriptHash) {
        this.scriptHash = scriptHash;
    }

    public WalletNode getWalletNode(Wallet wallet) {
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            WalletNode changedNode = getWalletNode(wallet, keyPurpose);
            if(changedNode != null) {
                return changedNode;
            }
        }

        Wallet notificationWallet = wallet.getNotificationWallet();
        if(notificationWallet != null) {
            WalletNode notificationNode = notificationWallet.getNode(KeyPurpose.NOTIFICATION);
            if(ElectrumServer.getScriptHash(notificationWallet, notificationNode).equals(scriptHash)) {
                return notificationNode;
            }
        }

        return null;
    }

    private WalletNode getWalletNode(Wallet wallet, KeyPurpose keyPurpose) {
        WalletNode purposeNode = wallet.getNode(keyPurpose);
        for(WalletNode addressNode : new ArrayList<>(purposeNode.getChildren())) {
            if(ElectrumServer.getScriptHash(wallet, addressNode).equals(scriptHash)) {
                return addressNode;
            }
        }

        return null;
    }

    public String getScriptHash() {
        return scriptHash;
    }
}
