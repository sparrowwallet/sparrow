package com.sparrowwallet.sparrow.whirlpool.dataSource;

import com.google.common.eventbus.Subscribe;
import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.api.backend.beans.WalletResponse;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.data.dataPersister.DataPersister;
import com.samourai.whirlpool.client.wallet.data.dataSource.WalletResponseDataSource;
import com.samourai.whirlpool.client.wallet.data.minerFee.MinerFeeSupplier;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.NewBlockEvent;
import com.sparrowwallet.sparrow.event.WalletAddressesChangedEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class SparrowDataSource extends WalletResponseDataSource {
    private static final Logger log = LoggerFactory.getLogger(SparrowDataSource.class);

    private final String walletIdentifierPrefix;

    public SparrowDataSource(
            WhirlpoolWallet whirlpoolWallet,
            HD_Wallet bip44w,
            DataPersister dataPersister)
            throws Exception {
        super(whirlpoolWallet, bip44w, dataPersister);

        // prefix matching <prefix>:master, :Premix, :Postmix
        this.walletIdentifierPrefix = getWhirlpoolWallet().getWalletIdentifier().replace(":master", "");
    }

    @Override
    public void open() throws Exception {
        super.open();
        EventManager.get().register(this);
    }

    @Override
    public void close() throws Exception {
        EventManager.get().unregister(this);
        super.close();
    }

    @Override
    protected WalletResponse fetchWalletResponse() throws Exception {
        WalletResponse walletResponse = new WalletResponse();
        walletResponse.wallet = new WalletResponse.Wallet();

        Map<Sha256Hash, BlockTransaction> allTransactions = new HashMap<>();
        Map<Sha256Hash, String> allTransactionsZpubs = new HashMap<>();
        List<WalletResponse.Address> addresses = new ArrayList<>();
        List<WalletResponse.Tx> txes = new ArrayList<>();
        List<UnspentOutput> unspentOutputs = new ArrayList<>();
        int storedBlockHeight = 0;

        String[] zpubs = getWalletSupplier().getPubs(true);
        for(String zpub : zpubs) {
            Wallet wallet = getWallet(zpub);
            if(wallet == null) {
                log.debug("No wallet for " + zpub + " found");
                continue;
            }

            allTransactions.putAll(wallet.getTransactions());
            wallet.getTransactions().keySet().forEach(txid -> allTransactionsZpubs.put(txid, zpub));
            if(wallet.getStoredBlockHeight() != null) {
                storedBlockHeight = Math.max(storedBlockHeight, wallet.getStoredBlockHeight());
            }

            WalletResponse.Address address = new WalletResponse.Address();
            List<ExtendedKey.Header> headers = ExtendedKey.Header.getHeaders(Network.get());
            ExtendedKey.Header header = headers.stream().filter(head -> head.getDefaultScriptType().equals(wallet.getScriptType()) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
            address.address = wallet.getKeystores().get(0).getExtendedPublicKey().toString(header);
            address.account_index = wallet.getNode(KeyPurpose.RECEIVE).getHighestUsedIndex() == null ? 0 : wallet.getNode(KeyPurpose.RECEIVE).getHighestUsedIndex() + 1;
            address.change_index = wallet.getNode(KeyPurpose.CHANGE).getHighestUsedIndex() == null ? 0 : wallet.getNode(KeyPurpose.CHANGE).getHighestUsedIndex() + 1;
            address.n_tx = wallet.getTransactions().size();
            addresses.add(address);

            for(Map.Entry<BlockTransactionHashIndex, WalletNode> utxo : wallet.getWalletUtxos().entrySet()) {
                BlockTransaction blockTransaction = wallet.getTransactions().get(utxo.getKey().getHash());
                if(blockTransaction != null && utxo.getKey().getStatus() != Status.FROZEN) {
                    unspentOutputs.add(Whirlpool.getUnspentOutput(wallet, utxo.getValue(), blockTransaction, (int)utxo.getKey().getIndex()));
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
                if(allTransactionsZpubs.containsKey(txInput.getOutpoint().getHash())) {
                    tx.inputs[i].prev_out = new WalletResponse.TxOut();
                    tx.inputs[i].prev_out.txid = txInput.getOutpoint().getHash().toString();
                    tx.inputs[i].prev_out.vout = (int)txInput.getOutpoint().getIndex();

                    BlockTransaction spentTransaction = allTransactions.get(txInput.getOutpoint().getHash());
                    if(spentTransaction != null) {
                        TransactionOutput spentOutput = spentTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
                        tx.inputs[i].prev_out.value = spentOutput.getValue();
                    }

                    tx.inputs[i].prev_out.xpub = new UnspentOutput.Xpub();
                    tx.inputs[i].prev_out.xpub.m = allTransactionsZpubs.get(txInput.getOutpoint().getHash());
                }
            }

            tx.out = new WalletResponse.TxOutput[blockTransaction.getTransaction().getOutputs().size()];
            for(int i = 0; i < blockTransaction.getTransaction().getOutputs().size(); i++) {
                TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get(i);
                tx.out[i] = new WalletResponse.TxOutput();
                tx.out[i].n = txOutput.getIndex();
                tx.out[i].value = txOutput.getValue();
                tx.out[i].xpub = new UnspentOutput.Xpub();
                tx.out[i].xpub.m = allTransactionsZpubs.get(blockTransaction.getHash());
            }

            txes.add(tx);
        }

        walletResponse.addresses = addresses.toArray(new WalletResponse.Address[0]);
        walletResponse.txs = txes.toArray(new WalletResponse.Tx[0]);
        walletResponse.unspent_outputs = unspentOutputs.toArray(new UnspentOutput[0]);

        walletResponse.info = new WalletResponse.Info();
        walletResponse.info.latest_block = new WalletResponse.InfoBlock();
        walletResponse.info.latest_block.height = AppServices.getCurrentBlockHeight() == null ? storedBlockHeight : AppServices.getCurrentBlockHeight();
        walletResponse.info.latest_block.hash = Sha256Hash.ZERO_HASH.toString();
        walletResponse.info.latest_block.time = AppServices.getLatestBlockHeader() == null ? 1 : AppServices.getLatestBlockHeader().getTime();

        walletResponse.info.fees = new LinkedHashMap<>();
        for(MinerFeeTarget target : MinerFeeTarget.values()) {
            walletResponse.info.fees.put(target.getValue(), getMinerFeeSupplier().getFee(target));
        }

        return walletResponse;
    }

    @Override
    public void pushTx(String txHex) throws Exception {
        Transaction transaction = new Transaction(Utils.hexToBytes(txHex));
        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.broadcastTransactionPrivately(transaction);
    }

    @Override
    public MinerFeeSupplier getMinerFeeSupplier() {
        return SparrowMinerFeeSupplier.getInstance();
    }

    static Wallet getWallet(String zpub) {
        return AppServices.get().getOpenWallets().keySet().stream()
                .filter(wallet -> {
                    List<ExtendedKey.Header> headers = ExtendedKey.Header.getHeaders(Network.get());
                    ExtendedKey.Header header = headers.stream().filter(head -> head.getDefaultScriptType().equals(wallet.getScriptType()) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
                    ExtendedKey extPubKey = wallet.getKeystores().get(0).getExtendedPublicKey();
                    return extPubKey.toString(header).equals(zpub);
                })
                .filter(Wallet::isValid)
                .findFirst()
                .orElse(null);
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        refreshWallet(event.getWalletId());
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        refreshWallet(event.getWalletId());
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        try {
            refresh();
        } catch (Exception e) {
            log.error("", e);
        }
    }

    private void refreshWallet(String eventWalletId) {
        try {
            // match <prefix>:master, :Premix, :Postmix
            if (eventWalletId.startsWith(walletIdentifierPrefix)) {
                refresh();
            }
        } catch (Exception e) {
            log.error("", e);
        }
    }
}
