package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.bip47.rpc.BIP47Wallet;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.java.Bip47UtilJava;
import com.samourai.wallet.bipFormat.BipFormat;
import com.samourai.wallet.cahoots.AbstractCahootsWallet;
import com.samourai.wallet.cahoots.CahootsUtxo;
import com.samourai.wallet.chain.ChainSupplier;
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
import org.bitcoinj.core.NetworkParameters;

import java.util.LinkedList;
import java.util.List;

public class SparrowCahootsWallet extends AbstractCahootsWallet {
    private final Wallet wallet;
    private final HD_Wallet bip84w;
    private final int account;
    private final List<CahootsUtxo> utxos;

    public SparrowCahootsWallet(ChainSupplier chainSupplier, Wallet wallet, HD_Wallet bip84w, int bip47Account) {
        super(chainSupplier, bip84w.getFingerprint(), new BIP47Wallet(bip84w).getAccount(bip47Account));
        this.wallet = wallet;
        this.bip84w = bip84w;
        this.account = wallet.getAccountIndex();
        this.utxos = new LinkedList<>();

        bip84w.getAccount(account).getReceive().setAddrIdx(wallet.getFreshNode(KeyPurpose.RECEIVE).getIndex());
        bip84w.getAccount(account).getChange().setAddrIdx(wallet.getFreshNode(KeyPurpose.CHANGE).getIndex());

        if(!wallet.isMasterWallet() && account != 0) {
            Wallet masterWallet = wallet.getMasterWallet();
            bip84w.getAccount(0).getReceive().setAddrIdx(masterWallet.getFreshNode(KeyPurpose.RECEIVE).getIndex());
            bip84w.getAccount(0).getChange().setAddrIdx(masterWallet.getFreshNode(KeyPurpose.CHANGE).getIndex());
        }
    }

    public Wallet getWallet() {
        return wallet;
    }

    public int getAccount() {
        return account;
    }

    @Override
    protected String doFetchAddressReceive(int account, boolean increment, BipFormat bipFormat) throws Exception {
        if(account == StandardAccount.WHIRLPOOL_POSTMIX.getAccountNumber()) {
            // force change chain
            return getAddress(account, KeyPurpose.CHANGE);
        }

        return getAddress(account, KeyPurpose.RECEIVE);
    }

    @Override
    protected String doFetchAddressChange(int account, boolean increment, BipFormat bipFormat) throws Exception {
        return getAddress(account, KeyPurpose.CHANGE);
    }

    @Override
    public List<CahootsUtxo> getUtxosWpkhByAccount(int account) {
        return utxos;
    }

    private String getAddress(int account, KeyPurpose keyPurpose) {
        return getWallet(account).getFreshNode(keyPurpose).getAddress().getAddress();
    }

    private Wallet getWallet(int account) {
        if(account != this.account && account == 0 && !wallet.isMasterWallet()) {
            return wallet.getMasterWallet();
        }

        return wallet;
    }
    public void addUtxo(WalletNode node, BlockTransaction blockTransaction, int index) {
        if(node.getWallet().getScriptType() != ScriptType.P2WPKH) {
            return;
        }

        NetworkParameters params = getBip47Account().getParams();
        UnspentOutput unspentOutput = Whirlpool.getUnspentOutput(node, blockTransaction, index);
        MyTransactionOutPoint myTransactionOutPoint = unspentOutput.computeOutpoint(params);

        CahootsUtxo cahootsUtxo;
        if(node.getWallet().isBip47()) {
            try {
                String strPaymentCode = node.getWallet().getKeystores().get(0).getExternalPaymentCode().toString();
                HD_Address hdAddress = getBip47Account().addressAt(node.getIndex());
                PaymentAddress paymentAddress = Bip47UtilJava.getInstance().getPaymentAddress(new PaymentCode(strPaymentCode), 0, hdAddress, params);
                cahootsUtxo = new CahootsUtxo(myTransactionOutPoint, node.getDerivationPath(), null, paymentAddress.getReceiveECKey().getPrivKeyBytes());
            } catch(Exception e) {
                throw new IllegalStateException("Cannot add BIP47 UTXO", e);
            }
        } else {
            HD_Address hdAddress = bip84w.getAddressAt(account, unspentOutput);
            cahootsUtxo = new CahootsUtxo(myTransactionOutPoint, node.getDerivationPath(), null, hdAddress.getECKey().getPrivKeyBytes());
        }

        utxos.add(cahootsUtxo);
    }
}
