package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.client.indexHandler.AbstractIndexHandler;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletGapLimitChangedEvent;
import com.sparrowwallet.sparrow.event.WalletMixConfigChangedEvent;
import com.sparrowwallet.sparrow.event.WhirlpoolIndexHighFrequencyEvent;

public class SparrowIndexHandler extends AbstractIndexHandler {
    private final Wallet wallet;
    private final WalletNode walletNode;
    private final int defaultValue;

    private static final long PERIOD = 1000 * 60 * 10L;  //Period of 10 minutes
    private long periodStart;
    private int periodCount;

    public SparrowIndexHandler(Wallet wallet, WalletNode walletNode) {
        this(wallet, walletNode, 0);
    }

    public SparrowIndexHandler(Wallet wallet, WalletNode walletNode, int defaultValue) {
        this.wallet = wallet;
        this.walletNode = walletNode;
        this.defaultValue = defaultValue;
    }

    @Override
    public synchronized int get() {
        return Math.max(getCurrentIndex(), getStoredIndex());
    }

    @Override
    public synchronized int getAndIncrement() {
        int index = get();
        set(index + 1);
        return index;
    }

    @Override
    public synchronized void set(int value) {
        setStoredIndex(value);
        ensureSufficientGapLimit(value);
    }

    private int getCurrentIndex() {
        Integer currentIndex = walletNode.getHighestUsedIndex();
        return currentIndex == null ? defaultValue : currentIndex + 1;
    }

    private int getStoredIndex() {
        if(wallet.getMixConfig() != null) {
            if(walletNode.getKeyPurpose() == KeyPurpose.RECEIVE) {
                return wallet.getMixConfig().getReceiveIndex();
            } else if(walletNode.getKeyPurpose() == KeyPurpose.CHANGE) {
                return wallet.getMixConfig().getChangeIndex();
            }
        }

        return defaultValue;
    }

    private void setStoredIndex(int index) {
        if(wallet.getMixConfig() != null) {
            if(walletNode.getKeyPurpose() == KeyPurpose.RECEIVE && wallet.getMixConfig().getReceiveIndex() != index) {
                wallet.getMixConfig().setReceiveIndex(index);
                EventManager.get().post(new WalletMixConfigChangedEvent(wallet));
            } else if(walletNode.getKeyPurpose() == KeyPurpose.CHANGE && wallet.getMixConfig().getChangeIndex() != index) {
                wallet.getMixConfig().setChangeIndex(index);
                EventManager.get().post(new WalletMixConfigChangedEvent(wallet));
            }
        }
    }

    private void ensureSufficientGapLimit(int index) {
        int highestUsedIndex = getCurrentIndex() - 1;
        int existingGapLimit = wallet.getGapLimit();
        if(index > highestUsedIndex + existingGapLimit) {
            wallet.setGapLimit(Math.max(wallet.getGapLimit(), index - highestUsedIndex));
            EventManager.get().post(new WalletGapLimitChangedEvent(getWalletId(), wallet, existingGapLimit));
            checkFrequency();
        }
    }

    private void checkFrequency() {
        if(periodStart > 0 && System.currentTimeMillis() - periodStart < PERIOD) {
            periodCount++;
        } else {
            periodStart = System.currentTimeMillis();
            periodCount = 0;
        }

        if(periodCount >= Wallet.DEFAULT_LOOKAHEAD) {
            EventManager.get().post(new WhirlpoolIndexHighFrequencyEvent(wallet));
        }
    }

    private String getWalletId() {
        try {
            return AppServices.get().getOpenWallets().get(wallet).getWalletId(wallet);
        } catch(Exception e) {
            return null;
        }
    }
}
