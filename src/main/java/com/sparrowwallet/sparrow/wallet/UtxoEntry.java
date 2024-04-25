package com.sparrowwallet.sparrow.wallet;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.wallet.*;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UtxoEntry extends HashIndexEntry {
    private final WalletNode node;

    public UtxoEntry(Wallet wallet, BlockTransactionHashIndex hashIndex, Type type, WalletNode node) {
        super(wallet, hashIndex, type, node.getKeyPurpose());
        this.node = node;
    }

    @Override
    public ObservableList<Entry> getChildren() {
        return FXCollections.emptyObservableList();
    }

    @Override
    public String getDescription() {
        return getHashIndex().getHash().toString().substring(0, 8) + "..:" + getHashIndex().getIndex();
    }

    @Override
    public boolean isSpent() {
        return false;
    }

    @Override
    public String getEntryType() {
        return "UTXO";
    }

    @Override
    public Function getWalletFunction() {
        return Function.UTXOS;
    }

    public Address getAddress() {
        return node.getAddress();
    }

    public WalletNode getNode() {
        return node;
    }

    public String getOutputDescriptor() {
        return node.getOutputDescriptor();
    }

    /**
     * Defines whether this utxo shares it's address with another utxo in the wallet
     */
    private ObjectProperty<AddressStatus> addressStatusProperty;

    public final void setDuplicateAddress(boolean value) {
        AddressStatus addressStatus = addressStatusProperty().get();
        addressStatusProperty().set(new AddressStatus(value, addressStatus.dustAttack));
    }

    public final void setDustAttack(boolean value) {
        AddressStatus addressStatus = addressStatusProperty().get();
        addressStatusProperty().set(new AddressStatus(addressStatus.duplicate, value));
    }

    public final boolean isDuplicateAddress() {
        return addressStatusProperty != null && addressStatusProperty.get().isDuplicate();
    }

    public final ObjectProperty<AddressStatus> addressStatusProperty() {
        if(addressStatusProperty == null) {
            addressStatusProperty = new SimpleObjectProperty<>(UtxoEntry.this, "addressStatus", new AddressStatus(false, false));
        }

        return addressStatusProperty;
    }

    public class AddressStatus {
        private final boolean duplicate;
        private final boolean dustAttack;

        public AddressStatus(boolean duplicate, boolean dustAttack) {
            this.duplicate = duplicate;
            this.dustAttack = dustAttack;
        }

        public UtxoEntry getUtxoEntry() {
            return UtxoEntry.this;
        }

        public Address getAddress() {
            return UtxoEntry.this.getAddress();
        }

        public boolean isDuplicate() {
            return duplicate;
        }

        public boolean isDustAttack() {
            return dustAttack;
        }
    }

    /**
     * Contains the mix status of this utxo, if available
     */
    private ObjectProperty<MixStatus> mixStatusProperty;

    public final MixStatus getMixStatus() {
        return mixStatusProperty == null ? null : mixStatusProperty.get();
    }

    public final ObjectProperty<MixStatus> mixStatusProperty() {
        if(mixStatusProperty == null) {
            mixStatusProperty = new SimpleObjectProperty<>(UtxoEntry.this, "mixStatus", new MixStatus());
        }

        return mixStatusProperty;
    }

    public class MixStatus {
        public UtxoEntry getUtxoEntry() {
            return UtxoEntry.this;
        }

        public UtxoMixData getUtxoMixData() {
            Wallet wallet = getUtxoEntry().getWallet().isMasterWallet() ? getUtxoEntry().getWallet() : getUtxoEntry().getWallet().getMasterWallet();
            if(wallet.getUtxoMixData(getHashIndex()) != null) {
                return wallet.getUtxoMixData(getHashIndex());
            }

            //Mix data not available - recount (and store if WhirlpoolWallet is running)
            if(getUtxoEntry().getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX && node.getKeyPurpose() == KeyPurpose.RECEIVE) {
                int mixesDone = recountMixesDone(getUtxoEntry().getWallet(), getHashIndex());
                return new UtxoMixData(mixesDone, null);
            }

            return new UtxoMixData(getUtxoEntry().getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX ? 1 : 0, null);
        }

        public int recountMixesDone(Wallet postmixWallet, BlockTransactionHashIndex postmixUtxo) {
            int mixesDone = 0;
            Set<BlockTransactionHashIndex> walletTxos = postmixWallet.getWalletTxos().entrySet().stream()
                    .filter(entry -> entry.getValue().getKeyPurpose() == KeyPurpose.RECEIVE).map(Map.Entry::getKey).collect(Collectors.toSet());
            BlockTransaction blkTx = postmixWallet.getTransactions().get(postmixUtxo.getHash());

            while(blkTx != null) {
                mixesDone++;
                List<TransactionInput> inputs = blkTx.getTransaction().getInputs();
                blkTx = null;
                for(TransactionInput txInput : inputs) {
                    BlockTransaction inputTx = postmixWallet.getTransactions().get(txInput.getOutpoint().getHash());
                    if(inputTx != null && walletTxos.stream().anyMatch(txo -> txo.getHash().equals(inputTx.getHash()) && txo.getIndex() == txInput.getOutpoint().getIndex()) && inputTx.getTransaction() != null) {
                        blkTx = inputTx;
                    }
                }
            }

            return mixesDone;
        }

        public int getMixesDone() {
            return getUtxoMixData().getMixesDone();
        }
    }
}
