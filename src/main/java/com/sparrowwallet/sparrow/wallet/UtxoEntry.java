package com.sparrowwallet.sparrow.wallet;

import com.samourai.whirlpool.client.mix.listener.MixFailReason;
import com.samourai.whirlpool.client.mix.listener.MixStep;
import com.samourai.whirlpool.client.wallet.beans.MixProgress;
import com.samourai.whirlpool.protocol.beans.Utxo;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

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

    public boolean isMixing() {
        return mixStatusProperty != null && ((mixStatusProperty.get().getMixProgress() != null && mixStatusProperty.get().getMixProgress().getMixStep() != MixStep.FAIL) || mixStatusProperty.get().getNextMixUtxo() != null);
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

    public void setMixProgress(MixProgress mixProgress) {
        mixStatusProperty().set(new MixStatus(mixProgress));
    }

    public void setMixFailReason(MixFailReason mixFailReason, String mixError) {
        mixStatusProperty().set(new MixStatus(mixFailReason, mixError));
    }

    public void setNextMixUtxo(Utxo nextMixUtxo) {
        mixStatusProperty().set(new MixStatus(nextMixUtxo));
    }

    public final MixStatus getMixStatus() {
        return mixStatusProperty == null ? null : mixStatusProperty.get();
    }

    public final ObjectProperty<MixStatus> mixStatusProperty() {
        if(mixStatusProperty == null) {
            mixStatusProperty = new SimpleObjectProperty<>(UtxoEntry.this, "mixStatus", null);
        }

        return mixStatusProperty;
    }

    public class MixStatus {
        private MixProgress mixProgress;
        private Utxo nextMixUtxo;
        private MixFailReason mixFailReason;
        private String mixError;
        private Long mixErrorTimestamp;

        public MixStatus(MixProgress mixProgress) {
            this.mixProgress = mixProgress;
        }

        public MixStatus(Utxo nextMixUtxo) {
            this.nextMixUtxo = nextMixUtxo;
        }

        public MixStatus(MixFailReason mixFailReason, String mixError) {
            this.mixFailReason = mixFailReason;
            this.mixError = mixError;
            this.mixErrorTimestamp = System.currentTimeMillis();
        }

        public UtxoEntry getUtxoEntry() {
            return UtxoEntry.this;
        }

        public UtxoMixData getUtxoMixData() {
            Wallet wallet = getUtxoEntry().getWallet().isMasterWallet() ? getUtxoEntry().getWallet() : getUtxoEntry().getWallet().getMasterWallet();
            if(wallet.getUtxoMixData(getHashIndex()) != null) {
                return wallet.getUtxoMixData(getHashIndex());
            }

            //Mix data not available - recount (and store if WhirlpoolWallet is running)
            Whirlpool whirlpool = AppServices.getWhirlpoolServices().getWhirlpool(wallet);
            if(whirlpool != null && getUtxoEntry().getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX && node.getKeyPurpose() == KeyPurpose.RECEIVE) {
                int mixesDone = whirlpool.recountMixesDone(getUtxoEntry().getWallet(), getHashIndex());
                whirlpool.setMixesDone(getHashIndex(), mixesDone);
                return new UtxoMixData(mixesDone, null);
            }

            return new UtxoMixData(getUtxoEntry().getWallet().getStandardAccountType() == StandardAccount.WHIRLPOOL_POSTMIX ? 1 : 0, null);
        }

        public int getMixesDone() {
            return getUtxoMixData().getMixesDone();
        }

        public MixProgress getMixProgress() {
            return mixProgress;
        }

        public Utxo getNextMixUtxo() {
            return nextMixUtxo;
        }

        public MixFailReason getMixFailReason() {
            return mixFailReason;
        }

        public String getMixError() {
            return mixError;
        }

        public Long getMixErrorTimestamp() {
            return mixErrorTimestamp;
        }
    }
}
