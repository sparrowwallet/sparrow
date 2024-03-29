package com.sparrowwallet.sparrow.io.ckcard;

import com.google.common.io.BaseEncoding;
import com.sparrowwallet.drongo.crypto.ChildNumber;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.WalletModel;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CardStatus extends CardResponse {
    int proto;
    String ver;
    BigInteger birth;
    boolean tapsigner;
    boolean satschip;
    List<BigInteger> path;
    BigInteger num_backups;
    List<BigInteger> slots;
    String addr;
    byte[] pubkey;
    BigInteger auth_delay;
    boolean testnet;

    public boolean isInitialized() {
        return ((getCardType() == WalletModel.TAPSIGNER || getCardType() == WalletModel.SATSCHIP) && path != null) || (getCardType() == WalletModel.SATSCARD && addr != null);
    }

    public String getIdentifier() {
        byte[] pubkeyHash = Sha256Hash.hash(pubkey);
        String base32 = BaseEncoding.base32().encode(Arrays.copyOfRange(pubkeyHash, 8, 32));
        return base32.replaceAll("(.{5})", "$1-");
    }

    public List<ChildNumber> getDerivation() {
        if(isInitialized()) {
            return path.stream().map(i -> new ChildNumber(i.intValue())).collect(Collectors.toList());
        }

        return null;
    }

    public boolean requiresBackup() {
        return !satschip && (num_backups == null || num_backups.intValue() == 0);
    }

    public WalletModel getCardType() {
        return satschip ? WalletModel.SATSCHIP : (tapsigner ? WalletModel.TAPSIGNER : WalletModel.SATSCARD);
    }

    public int getCurrentSlot() {
        if(slots == null || slots.isEmpty()) {
            return 0;
        }

        return slots.get(0).intValue();
    }

    public int getLastSlot() {
        if(slots == null || slots.size() < 2) {
            return 0;
        }

        return slots.get(1).intValue();
    }

    @Override
    public String toString() {
        return "CardStatus{" +
                "proto=" + proto +
                ", ver='" + ver + '\'' +
                ", birth=" + birth +
                ", tapsigner=" + tapsigner +
                ", satschip=" + satschip +
                ", path=" + path +
                ", num_backups=" + num_backups +
                ", slots=" + slots +
                ", addr='" + addr + '\'' +
                ", pubkey=" + Arrays.toString(pubkey) +
                ", auth_delay=" + auth_delay +
                ", testnet=" + testnet +
                '}';
    }
}
