package com.sparrowwallet.sparrow.soroban;

import com.samourai.soroban.client.SorobanServer;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.cahoots.CahootsUtxo;
import com.samourai.wallet.cahoots.SimpleCahootsWallet;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import org.apache.commons.lang3.tuple.Pair;

import java.util.LinkedList;
import java.util.List;

public class SparrowCahootsWallet extends SimpleCahootsWallet {
    private final Wallet wallet;
    private final int account;
    private final int bip47Account;

    public SparrowCahootsWallet(Wallet wallet, HD_Wallet bip84w, int bip47Account, SorobanServer sorobanServer, long feePerB) throws Exception {
        super(bip84w, sorobanServer.getParams(), wallet.getFreshNode(KeyPurpose.CHANGE).getIndex(), feePerB);
        this.wallet = wallet;
        this.account = wallet.getAccountIndex();
        this.bip47Account = bip47Account;
        bip84w.getAccount(account).getReceive().setAddrIdx(wallet.getFreshNode(KeyPurpose.RECEIVE).getIndex());
        bip84w.getAccount(account).getChange().setAddrIdx(wallet.getFreshNode(KeyPurpose.CHANGE).getIndex());

        if(!wallet.isMasterWallet() && account != 0) {
            Wallet masterWallet = wallet.getMasterWallet();
            bip84w.getAccount(0).getReceive().setAddrIdx(masterWallet.getFreshNode(KeyPurpose.RECEIVE).getIndex());
            bip84w.getAccount(0).getChange().setAddrIdx(masterWallet.getFreshNode(KeyPurpose.CHANGE).getIndex());
        }
    }

    public void addUtxo(WalletNode node, BlockTransaction blockTransaction, int index) {
        if(node.getWallet().getScriptType() != ScriptType.P2WPKH) {
            return;
        }

        UnspentOutput unspentOutput = Whirlpool.getUnspentOutput(node, blockTransaction, index);
        MyTransactionOutPoint myTransactionOutPoint = unspentOutput.computeOutpoint(getParams());

        CahootsUtxo cahootsUtxo;
        if(node.getWallet().isBip47()) {
            try {
                String strPaymentCode = node.getWallet().getKeystores().get(0).getExternalPaymentCode().toString();
                HD_Address hdAddress = getBip47Wallet().getAccount(getBip47Account()).addressAt(node.getIndex());
                PaymentAddress paymentAddress = Bip47UtilJava.getInstance().getPaymentAddress(new PaymentCode(strPaymentCode), 0, hdAddress, getParams());
                cahootsUtxo = new CahootsUtxo(myTransactionOutPoint, node.getDerivationPath(), paymentAddress.getReceiveECKey());
            } catch(Exception e) {
                throw new IllegalStateException("Cannot add BIP47 UTXO", e);
            }
        } else {
            HD_Address hdAddress = getBip84Wallet().getAddressAt(account, unspentOutput);
            cahootsUtxo = new CahootsUtxo(myTransactionOutPoint, node.getDerivationPath(), hdAddress.getECKey());
        }

        addUtxo(account, cahootsUtxo);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public int getAccount() {
        return account;
    }

    @Override
    protected List<CahootsUtxo> fetchUtxos(int account) {
        List<CahootsUtxo> utxos = super.fetchUtxos(account);
        if(utxos == null) {
            utxos = new LinkedList<>();
        }

        return utxos;
    }

    @Override
    public Pair<Integer, Integer> fetchReceiveIndex(int account) throws Exception {
        if(account == StandardAccount.WHIRLPOOL_POSTMIX.getAccountNumber()) {
            // force change chain
            return Pair.of(getWallet(account).getFreshNode(KeyPurpose.CHANGE).getIndex(), 1);
        }

        return Pair.of(getWallet(account).getFreshNode(KeyPurpose.RECEIVE).getIndex(), 0);
    }

    @Override
    public Pair<Integer, Integer> fetchChangeIndex(int account) throws Exception {
        return Pair.of(getWallet(account).getFreshNode(KeyPurpose.CHANGE).getIndex(), 1);
    }

    private Wallet getWallet(int account) {
        if(account != this.account && account == 0 && !wallet.isMasterWallet()) {
            return wallet.getMasterWallet();
        }

        return wallet;
    }

    @Override
    public int getBip47Account() {
        return bip47Account;
    }
}
