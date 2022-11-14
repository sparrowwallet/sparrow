package com.sparrowwallet.sparrow.soroban;

import com.samourai.wallet.cahoots.Cahoots;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.protocol.TransactionInput;
import com.sparrowwallet.drongo.protocol.TransactionOutput;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.TransactionDiagram;
import com.sparrowwallet.sparrow.event.WalletConfigChangedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Taskbar;
import java.util.*;
import java.util.stream.Collectors;

public class SorobanController {
    private static final Logger log = LoggerFactory.getLogger(SorobanController.class);

    protected Transaction getTransaction(Cahoots cahoots) throws PSBTParseException {
        if(cahoots.getPSBT() != null) {
            PSBT psbt = new PSBT(cahoots.getPSBT().toBytes());
            return psbt.getTransaction();
        }

        return null;
    }

    protected void updateTransactionDiagram(TransactionDiagram transactionDiagram, Wallet wallet, WalletTransaction walletTransaction, Transaction transaction) {
        WalletTransaction txWalletTransaction = getWalletTransaction(wallet, walletTransaction, transaction, null);
        transactionDiagram.update(txWalletTransaction);

        if(txWalletTransaction.getSelectedUtxoSets().size() == 2) {
            Set<Sha256Hash> references = txWalletTransaction.getSelectedUtxoSets().get(1).keySet().stream().map(BlockTransactionHash::getHash).collect(Collectors.toSet());
            ElectrumServer.TransactionReferenceService transactionReferenceService = new ElectrumServer.TransactionReferenceService(references);
            transactionReferenceService.setOnSucceeded(successEvent -> {
                Map<Sha256Hash, BlockTransaction> transactionMap = transactionReferenceService.getValue();
                transactionDiagram.update(getWalletTransaction(wallet, walletTransaction, transaction, transactionMap));
            });
            transactionReferenceService.setOnFailed(failedEvent -> {
                log.error("Failed to retrieve referenced transactions", failedEvent.getSource().getException());
            });
            transactionReferenceService.start();
        }
    }

    private WalletTransaction getWalletTransaction(Wallet wallet, WalletTransaction walletTransaction, Transaction transaction, Map<Sha256Hash, BlockTransaction> inputTransactions) {
        Map<BlockTransactionHashIndex, WalletNode> allWalletUtxos = wallet.getWalletTxos();
        Map<BlockTransactionHashIndex, WalletNode> walletUtxos = new LinkedHashMap<>();
        Map<BlockTransactionHashIndex, WalletNode> externalUtxos = new LinkedHashMap<>();

        for(TransactionInput txInput : transaction.getInputs()) {
            Optional<BlockTransactionHashIndex> optWalletUtxo = allWalletUtxos.keySet().stream().filter(txo -> txo.getHash().equals(txInput.getOutpoint().getHash()) && txo.getIndex() == txInput.getOutpoint().getIndex()).findFirst();
            if(optWalletUtxo.isPresent()) {
                walletUtxos.put(optWalletUtxo.get(), allWalletUtxos.get(optWalletUtxo.get()));
            } else {
                BlockTransactionHashIndex externalUtxo;
                if(inputTransactions != null && inputTransactions.containsKey(txInput.getOutpoint().getHash())) {
                    BlockTransaction blockTransaction = inputTransactions.get(txInput.getOutpoint().getHash());
                    TransactionOutput txOutput = blockTransaction.getTransaction().getOutputs().get((int)txInput.getOutpoint().getIndex());
                    externalUtxo = new BlockTransactionHashIndex(blockTransaction.getHash(), blockTransaction.getHeight(), blockTransaction.getDate(), blockTransaction.getFee(), txInput.getOutpoint().getIndex(), txOutput.getValue());
                } else {
                    externalUtxo = new BlockTransactionHashIndex(txInput.getOutpoint().getHash(), 0, null, null, txInput.getOutpoint().getIndex(), 0);
                }
                externalUtxos.put(externalUtxo, null);
            }
        }

        List<Map<BlockTransactionHashIndex, WalletNode>> selectedUtxoSets = new ArrayList<>();
        selectedUtxoSets.add(walletUtxos);
        selectedUtxoSets.add(externalUtxos);

        Map<Address, WalletNode> walletAddresses = wallet.getWalletAddresses();
        List<Payment> payments = new ArrayList<>();
        Map<WalletNode, Long> changeMap = new LinkedHashMap<>();
        for(TransactionOutput txOutput : transaction.getOutputs()) {
            Address address = txOutput.getScript().getToAddress();
            if(address != null) {
                Optional<Payment> optPayment = walletTransaction == null ? Optional.empty() :
                        walletTransaction.getPayments().stream().filter(payment -> payment.getAddress().equals(address) && payment.getAmount() == txOutput.getValue()).findFirst();
                if(optPayment.isPresent()) {
                    payments.add(optPayment.get());
                } else if(walletAddresses.containsKey(address) && walletAddresses.get(address).getKeyPurpose() == KeyPurpose.CHANGE) {
                    changeMap.put(walletAddresses.get(address), txOutput.getValue());
                } else {
                    Payment payment = new Payment(address, null, txOutput.getValue(), false);
                    if(transaction.getOutputs().stream().anyMatch(txo -> txo != txOutput && txo.getValue() == txOutput.getValue())) {
                        payment.setType(Payment.Type.MIX);
                    }
                    payments.add(payment);
                }
            }
        }

        long fee = calculateFee(walletTransaction, selectedUtxoSets, transaction);
        return new WalletTransaction(wallet, transaction, Collections.emptyList(), selectedUtxoSets, payments, changeMap, fee, inputTransactions);
    }

    private long calculateFee(WalletTransaction walletTransaction, List<Map<BlockTransactionHashIndex, WalletNode>> selectedUtxoSets, Transaction transaction) {
        Map<BlockTransactionHashIndex, WalletNode> selectedUtxos = new LinkedHashMap<>();
        selectedUtxoSets.forEach(selectedUtxos::putAll);

        long feeAmt = 0L;
        for(BlockTransactionHashIndex utxo : selectedUtxos.keySet()) {
            if(utxo.getValue() == 0) {
                return walletTransaction == null ? -1 : walletTransaction.getFee();
            }

            feeAmt += utxo.getValue();
        }

        for(TransactionOutput txOutput : transaction.getOutputs()) {
            feeAmt -= txOutput.getValue();
        }

        return feeAmt;
    }

    protected void requestUserAttention() {
        if(Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.USER_ATTENTION)) {
            Taskbar.getTaskbar().requestUserAttention(true, false);
        }
    }

    protected boolean isUsePayNym(Wallet wallet) {
        //TODO: Remove config setting
        boolean usePayNym = Config.get().isUsePayNym();
        if(usePayNym && wallet != null) {
            setUsePayNym(wallet, true);
        }

        return usePayNym;
    }

    protected void setUsePayNym(Wallet wallet, boolean usePayNym) {
        //TODO: Remove config setting
        if(Config.get().isUsePayNym() != usePayNym) {
            Config.get().setUsePayNym(usePayNym);
        }

        if(wallet != null) {
            WalletConfig walletConfig = wallet.getMasterWalletConfig();
            if(walletConfig.isUsePayNym() != usePayNym) {
                walletConfig.setUsePayNym(usePayNym);
                EventManager.get().post(new WalletConfigChangedEvent(wallet.isMasterWallet() ? wallet : wallet.getMasterWallet()));
            }
        }
    }
}
