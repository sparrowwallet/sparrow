package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.api.backend.beans.WalletResponse;
import com.samourai.wallet.bipWallet.BipWallet;
import com.samourai.wallet.bipWallet.WalletSupplier;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxo;
import com.samourai.whirlpool.client.wallet.data.dataSource.DataSourceConfig;
import com.samourai.whirlpool.client.wallet.data.utxo.BasicUtxoSupplier;
import com.samourai.whirlpool.client.wallet.data.utxo.UtxoData;
import com.samourai.whirlpool.client.wallet.data.utxoConfig.UtxoConfigSupplier;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import org.bitcoinj.core.NetworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

// manages utxos & wallet indexes
public class SparrowUtxoSupplier extends BasicUtxoSupplier {
    private static final Logger log = LoggerFactory.getLogger(SparrowUtxoSupplier.class);

    public SparrowUtxoSupplier(
            WalletSupplier walletSupplier,
            UtxoConfigSupplier utxoConfigSupplier,
            DataSourceConfig dataSourceConfig,
            NetworkParameters params) {
        super(walletSupplier, utxoConfigSupplier, dataSourceConfig, params);
    }

    @Override
    public void refresh() throws Exception {
        Map<Sha256Hash, BlockTransaction> allTransactions = new HashMap<>();
        Map<Sha256Hash, String> allTransactionsXpubs = new HashMap<>();
        List<WalletResponse.Tx> txes = new ArrayList<>();
        List<UnspentOutput> unspentOutputs = new ArrayList<>();
        int storedBlockHeight = 0;

        Collection<BipWallet> bipWallets = getWalletSupplier().getWallets();
        for(BipWallet bipWallet : bipWallets) {
            String zpub = bipWallet.getBipPub();
            Wallet wallet = SparrowDataSource.getWallet(zpub);
            if(wallet == null) {
                log.debug("No wallet for " + zpub + " found");
                continue;
            }

            Map<Sha256Hash, BlockTransaction> walletTransactions = wallet.getWalletTransactions();
            allTransactions.putAll(walletTransactions);
            String xpub = bipWallet.getXPub();
            walletTransactions.keySet().forEach(txid -> allTransactionsXpubs.put(txid, xpub));
            if(wallet.getStoredBlockHeight() != null) {
                storedBlockHeight = Math.max(storedBlockHeight, wallet.getStoredBlockHeight());
            }

            // update wallet index: receive
            int receiveIndex = wallet.getNode(KeyPurpose.RECEIVE).getHighestUsedIndex() == null ? 0 : wallet.getNode(KeyPurpose.RECEIVE).getHighestUsedIndex() + 1;
            int account_index = wallet.getMixConfig() != null ? Math.max(receiveIndex, wallet.getMixConfig().getReceiveIndex()) : receiveIndex;
            bipWallet.getIndexHandlerReceive().set(account_index, false);

            // update wallet index: change
            int changeIndex = wallet.getNode(KeyPurpose.CHANGE).getHighestUsedIndex() == null ? 0 : wallet.getNode(KeyPurpose.CHANGE).getHighestUsedIndex() + 1;
            int change_index = wallet.getMixConfig() != null ? Math.max(changeIndex, wallet.getMixConfig().getChangeIndex()) : changeIndex;
            bipWallet.getIndexHandlerChange().set(change_index, false);

            for(Map.Entry<BlockTransactionHashIndex, WalletNode> utxo : wallet.getSpendableUtxos().entrySet()) {
                BlockTransaction blockTransaction = wallet.getWalletTransaction(utxo.getKey().getHash());
                if(blockTransaction != null) {
                    unspentOutputs.add(Whirlpool.getUnspentOutput(utxo.getValue(), blockTransaction, (int)utxo.getKey().getIndex()));
                }
            }
        }

        for(BlockTransaction blockTransaction : allTransactions.values()) {
            WalletResponse.Tx tx = new WalletResponse.Tx();
            tx.block_height = blockTransaction.getHeight();
            tx.hash = blockTransaction.getHashAsString();
            tx.locktime = blockTransaction.getTransaction().getLocktime();
            tx.version = (int)blockTransaction.getTransaction().getVersion();

            tx.inputs = new WalletResponse.TxInput[blockTransaction.getTransaction().getInputs().size()];
            for(int i = 0; i < blockTransaction.getTransaction().getInputs().size(); i++) {
                TransactionInput txInput = blockTransaction.getTransaction().getInputs().get(i);
                tx.inputs[i] = new WalletResponse.TxInput();
                tx.inputs[i].vin = txInput.getIndex();
                tx.inputs[i].sequence = txInput.getSequenceNumber();
                if(allTransactionsXpubs.containsKey(txInput.getOutpoint().getHash())) {
                    tx.inputs[i].prev_out = new WalletResponse.TxOut();
                    tx.inputs[i].prev_out.txid = txInput.getOutpoint().getHash().toString();
                    tx.inputs[i].prev_out.vout = (int)txInput.getOutpoint().getIndex();

                    BlockTransaction spentTransaction = allTransactions.get(txInput.getOutpoint().getHash());
                    if(spentTransaction != null) {
                        TransactionOutput spentOutput = spentTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
                        tx.inputs[i].prev_out.value = spentOutput.getValue();
                    }

                    tx.inputs[i].prev_out.xpub = new UnspentOutput.Xpub();
                    tx.inputs[i].prev_out.xpub.m = allTransactionsXpubs.get(txInput.getOutpoint().getHash());
                }
            }

            tx.out = new WalletResponse.TxOutput[blockTransaction.getTransaction().getOutputs().size()];
            for(int i = 0; i < blockTransaction.getTransaction().getOutputs().size(); i++) {
                TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get(i);
                tx.out[i] = new WalletResponse.TxOutput();
                tx.out[i].n = txOutput.getIndex();
                tx.out[i].value = txOutput.getValue();
                tx.out[i].xpub = new UnspentOutput.Xpub();
                tx.out[i].xpub.m = allTransactionsXpubs.get(blockTransaction.getHash());
            }

            txes.add(tx);
        }

        // update utxos
        UnspentOutput[] uos = unspentOutputs.toArray(new UnspentOutput[0]);
        WalletResponse.Tx[] txs = txes.toArray(new WalletResponse.Tx[0]);
        UtxoData utxoData = new UtxoData(uos, txs, storedBlockHeight);
        setValue(utxoData);
    }

    @Override
    public byte[] _getPrivKeyBip47(UnspentOutput utxo) throws Exception {
        Wallet wallet = SparrowDataSource.getWallet(utxo.xpub.m);
        Map<BlockTransactionHashIndex, WalletNode> walletUtxos = wallet.getWalletUtxos();
        WalletNode node = walletUtxos.entrySet().stream()
                .filter(entry -> entry.getKey().getHash().equals(Sha256Hash.wrap(utxo.tx_hash)) && entry.getKey().getIndex() == utxo.tx_output_n)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cannot find UTXO " + utxo));

        if(node.getWallet().isBip47()) {
            try {
                Keystore keystore = node.getWallet().getKeystores().get(0);
                return keystore.getKey(node).getPrivKeyBytes();
            } catch(Exception e) {
                log.error("Error getting private key", e);
            }
        }

        return null;
    }
}
