package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.whirlpool.client.wallet.data.walletState.WalletStateData;
import com.samourai.whirlpool.client.wallet.data.walletState.WalletStatePersister;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;

import java.util.LinkedHashMap;
import java.util.Map;

public class SparrowWalletStatePersister extends WalletStatePersister {
    private final String walletId;

    public SparrowWalletStatePersister(String walletId) {
        super(walletId);
        this.walletId = walletId;
    }

    @Override
    public synchronized WalletStateData load() throws Exception {
        Wallet wallet = AppServices.get().getOpenWallets().entrySet().stream().filter(entry -> entry.getValue().getWalletId(entry.getKey()).equals(walletId)).map(Map.Entry::getKey).findFirst().orElseThrow();

        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("init", 1);
        putValues("DEPOSIT", wallet, values);

        for(StandardAccount whirlpoolAccount : StandardAccount.WHIRLPOOL_ACCOUNTS) {
            putValues(whirlpoolAccount.getName().toUpperCase(), wallet.getChildWallet(whirlpoolAccount), values);
        }

        return new WalletStateData(values);
    }

    private void putValues(String prefix, Wallet wallet, Map<String, Integer> values) {
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            Integer index = wallet.getNode(keyPurpose).getHighestUsedIndex();
            values.put(prefix + "_" + getPurpose(wallet) + "_" + keyPurpose.getPathIndex().num(), index == null ? 0 : index + 1);
        }
    }

    private int getPurpose(Wallet wallet) {
        ScriptType scriptType = wallet.getScriptType();
        return scriptType.getDefaultDerivation().get(0).num();
    }

    @Override
    public synchronized void write(WalletStateData data) throws Exception {
        //nothing required
    }
}
