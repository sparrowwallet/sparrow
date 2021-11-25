package com.sparrowwallet.sparrow.soroban;

import com.samourai.soroban.client.SorobanServer;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.cahoots.CahootsUtxo;
import com.samourai.wallet.cahoots.SimpleCahootsWallet;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import org.apache.commons.lang3.tuple.Pair;

public class SparrowCahootsWallet extends SimpleCahootsWallet {
    private final Wallet wallet;
    private final int account;

    public SparrowCahootsWallet(Wallet wallet, HD_Wallet bip84w, SorobanServer sorobanServer, long feePerB) throws Exception {
        super(bip84w, sorobanServer.getParams(), wallet.getFreshNode(KeyPurpose.CHANGE).getIndex(), feePerB);
        this.wallet = wallet;
        this.account = wallet.getAccountIndex();
        bip84w.getAccount(account).getReceive().setAddrIdx(wallet.getFreshNode(KeyPurpose.RECEIVE).getIndex());
        bip84w.getAccount(account).getChange().setAddrIdx(wallet.getFreshNode(KeyPurpose.CHANGE).getIndex());
    }

    public void addUtxo(Wallet wallet, WalletNode node, BlockTransaction blockTransaction, int index) {
        UnspentOutput unspentOutput = Whirlpool.getUnspentOutput(wallet, node, blockTransaction, index);
        MyTransactionOutPoint myTransactionOutPoint = unspentOutput.computeOutpoint(getParams());
        HD_Address hdAddress = getBip84Wallet().getAddressAt(account, unspentOutput);
        CahootsUtxo cahootsUtxo = new CahootsUtxo(myTransactionOutPoint, node.getDerivationPath(), hdAddress.getECKey());
        addUtxo(account, cahootsUtxo);
    }

    public int getAccount() {
        return account;
    }

    @Override
    public Pair<Integer, Integer> fetchReceiveIndex(int account) throws Exception {
        if(account == StandardAccount.WHIRLPOOL_POSTMIX.getAccountNumber()) {
            // force change chain
            return Pair.of(wallet.getFreshNode(KeyPurpose.CHANGE).getIndex(), 1);
        }

        return Pair.of(wallet.getFreshNode(KeyPurpose.RECEIVE).getIndex(), 0);
    }

    @Override
    public Pair<Integer, Integer> fetchChangeIndex(int account) throws Exception {
        return Pair.of(wallet.getFreshNode(KeyPurpose.CHANGE).getIndex(), 1);
    }
}
