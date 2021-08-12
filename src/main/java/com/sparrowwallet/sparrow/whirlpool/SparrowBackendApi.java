package com.sparrowwallet.sparrow.whirlpool;

import com.samourai.wallet.api.backend.BackendApi;
import com.samourai.wallet.api.backend.MinerFee;
import com.samourai.wallet.api.backend.MinerFeeTarget;
import com.samourai.wallet.api.backend.beans.*;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.*;
import com.sparrowwallet.drongo.wallet.BlockTransaction;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@SuppressWarnings("deprecation")
public class SparrowBackendApi extends BackendApi {
    private static final Logger log = LoggerFactory.getLogger(SparrowBackendApi.class);
    private static final int FALLBACK_FEE_RATE = 75;

    public SparrowBackendApi() {
        super(null, null);
    }

    @Override
    public TxsResponse fetchTxs(String[] zpubs, int page, int count) throws Exception {
        List<TxsResponse.Tx> txes = new ArrayList<>();

        for(String zpub : zpubs) {
            Wallet wallet = getWallet(zpub);
            if(wallet == null) {
                log.debug("No wallet for " + zpub + " found");
                continue;
            }

            for(BlockTransaction blockTransaction : wallet.getTransactions().values()) {
                TxsResponse.Tx tx = new TxsResponse.Tx();
                tx.block_height = blockTransaction.getHeight();
                tx.hash = blockTransaction.getHashAsString();
                tx.locktime = blockTransaction.getTransaction().getLocktime();
                tx.time = blockTransaction.getDate().getTime();
                tx.version = (int)blockTransaction.getTransaction().getVersion();

                tx.inputs = new TxsResponse.TxInput[blockTransaction.getTransaction().getInputs().size()];
                for(int i = 0; i < blockTransaction.getTransaction().getInputs().size(); i++) {
                    TransactionInput txInput = blockTransaction.getTransaction().getInputs().get(i);
                    tx.inputs[i] = new TxsResponse.TxInput();
                    tx.inputs[i].vin = txInput.getIndex();
                    tx.inputs[i].sequence = txInput.getSequenceNumber();
                    tx.inputs[i].prev_out = new TxsResponse.TxOut();
                    tx.inputs[i].prev_out.txid = txInput.getOutpoint().getHash().toString();
                    tx.inputs[i].prev_out.vout = (int)txInput.getOutpoint().getIndex();

                    BlockTransaction spentTransaction = wallet.getTransactions().get(txInput.getOutpoint().getHash());
                    if(spentTransaction != null) {
                        TransactionOutput spentOutput = spentTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
                        tx.inputs[i].prev_out.value = spentOutput.getValue();
                        Address[] addresses = spentOutput.getScript().getToAddresses();
                        if(addresses.length > 0) {
                            tx.inputs[i].prev_out.addr = addresses[0].toString();
                        }
                    }
                }

                tx.out = new TxsResponse.TxOutput[blockTransaction.getTransaction().getOutputs().size()];
                for(int i = 0; i < blockTransaction.getTransaction().getOutputs().size(); i++) {
                    TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get(i);
                    tx.out[i].n = txOutput.getIndex();
                    tx.out[i].value = txOutput.getValue();
                    Address[] addresses = txOutput.getScript().getToAddresses();
                    if(addresses.length > 0) {
                        tx.out[i].addr = addresses[0].toString();
                    }
                }

                txes.add(tx);
            }
        }

        List<TxsResponse.Tx> pageTxes;
        if(txes.size() < count) {
            pageTxes = txes;
        } else {
            pageTxes = txes.subList(page * count, Math.min((page * count) + count, txes.size()));
        }

        TxsResponse txsResponse = new TxsResponse();
        txsResponse.n_tx = txes.size();
        txsResponse.page = page;
        txsResponse.n_tx_page = pageTxes.size();
        txsResponse.txs = pageTxes.toArray(new TxsResponse.Tx[0]);

        return txsResponse;
    }

    @Override
    public WalletResponse fetchWallet(String[] zpubs) throws Exception {
        WalletResponse walletResponse = new WalletResponse();
        walletResponse.wallet = new WalletResponse.Wallet();

        Map<Sha256Hash, BlockTransaction> allTransactions = new HashMap<>();
        Map<Sha256Hash, String> allTransactionsZpubs = new HashMap<>();
        List<WalletResponse.Address> addresses = new ArrayList<>();
        List<WalletResponse.Tx> txes = new ArrayList<>();
        List<UnspentOutput> unspentOutputs = new ArrayList<>();
        int storedBlockHeight = 0;

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
                if(blockTransaction != null) {
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
            walletResponse.info.fees.put(target.getValue(), AppServices.getTargetBlockFeeRates() == null ? FALLBACK_FEE_RATE : getMinimumFeeForTarget(Integer.parseInt(target.getValue())));
        }

        return walletResponse;
    }

    @Override
    public MinerFee fetchMinerFee() throws Exception {
        Map<String, Integer> fees = new LinkedHashMap<>();
        for(MinerFeeTarget target : MinerFeeTarget.values()) {
           fees.put(target.getValue(), AppServices.getTargetBlockFeeRates() == null ? FALLBACK_FEE_RATE : getMinimumFeeForTarget(Integer.parseInt(target.getValue())));
        }

        return new MinerFee(fees);
    }

    @Override
    public void pushTx(String txHex) throws Exception {
        Transaction transaction = new Transaction(Utils.hexToBytes(txHex));
        ElectrumServer electrumServer = new ElectrumServer();
        electrumServer.broadcastTransactionPrivately(transaction);
    }

    @Override
    public boolean testConnectivity() {
        return AppServices.isConnected();
    }

    private Integer getMinimumFeeForTarget(int targetBlocks) {
        List<Map.Entry<Integer, Double>> feeRates = new ArrayList<>(AppServices.getTargetBlockFeeRates().entrySet());
        Collections.reverse(feeRates);
        for(Map.Entry<Integer, Double> feeRate : feeRates) {
            if(feeRate.getKey() <= targetBlocks) {
                return feeRate.getValue().intValue();
            }
        }

        return feeRates.get(0).getValue().intValue();
    }

    @Override
    public void initBip84(String zpub) throws Exception {
        //nothing required
    }

    private Wallet getWallet(String zpub) {
        return AppServices.get().getOpenWallets().keySet().stream()
                .filter(wallet -> {
                    List<ExtendedKey.Header> headers = ExtendedKey.Header.getHeaders(Network.get());
                    ExtendedKey.Header header = headers.stream().filter(head -> head.getDefaultScriptType().equals(wallet.getScriptType()) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
                    ExtendedKey.Header p2pkhHeader = headers.stream().filter(head -> head.getDefaultScriptType().equals(ScriptType.P2PKH) && !head.isPrivateKey()).findFirst().orElse(ExtendedKey.Header.xpub);
                    ExtendedKey extPubKey = wallet.getKeystores().get(0).getExtendedPublicKey();
                    return extPubKey.toString(header).equals(zpub) || extPubKey.toString(p2pkhHeader).equals(zpub);
                })
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<UnspentOutput> fetchUtxos(String zpub) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UnspentOutput> fetchUtxos(String[] zpubs) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, MultiAddrResponse.Address> fetchAddresses(String[] zpubs) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public MultiAddrResponse.Address fetchAddress(String zpub) throws Exception {
        throw new UnsupportedOperationException();
    }
}
