package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.protocol.Sha256Hash;
import com.sparrowwallet.drongo.wallet.*;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.StorageException;
import com.sparrowwallet.sparrow.net.AllHistoryChangedException;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.drongo.wallet.WalletNode.nodeRangesToString;

public class WalletForm {
    private static final Logger log = LoggerFactory.getLogger(WalletForm.class);

    private final Storage storage;
    protected Wallet wallet;
    private Wallet savedPastWallet;

    private WalletTransactionsEntry walletTransactionsEntry;
    private WalletUtxosEntry walletUtxosEntry;
    private final List<NodeEntry> accountEntries = new ArrayList<>();
    private final List<Set<WalletNode>> walletTransactionNodes = new ArrayList<>();
    private final ObjectProperty<WalletTransaction> createdWalletTransactionProperty = new SimpleObjectProperty<>(null);

    private ElectrumServer.TransactionMempoolService transactionMempoolService;

    private final BooleanProperty lockedProperty = new SimpleBooleanProperty(false);

    public WalletForm(Storage storage, Wallet currentWallet, Wallet backupWallet) {
        this(storage, currentWallet, backupWallet, true);
    }

    public WalletForm(Storage storage, Wallet currentWallet, Wallet backupWallet, boolean refreshHistory) {
        this.storage = storage;
        this.wallet = currentWallet;

        //Unencrypted wallets load before isConnected is true, waiting for the ConnectionEvent to refresh history - save the backup for this event
        savedPastWallet = backupWallet;

        if(refreshHistory && wallet.isValid()) {
            ElectrumServer.addCalculatedScriptHashes(wallet);
            refreshHistory(AppServices.getCurrentBlockHeight(), backupWallet);
        }
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Wallet getMasterWallet() {
        return wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
    }

    public String getMasterWalletId() {
        return storage.getWalletId(getMasterWallet());
    }

    public Storage getStorage() {
        return storage;
    }

    public String getWalletId() {
        return storage.getWalletId(wallet);
    }

    public File getWalletFile() {
        return storage.getWalletFile();
    }

    public void setWallet(Wallet wallet) {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports setWallet");
    }

    public void revert() {
        throw new UnsupportedOperationException("Only SettingsWalletForm supports revert");
    }

    public void save() throws IOException, StorageException {
        storage.saveWallet(wallet);
    }

    public void saveAndRefresh() throws IOException, StorageException {
        Wallet pastWallet = wallet.copy();
        storage.backupTempWallet();
        wallet.clearHistory();
        save();
        refreshHistory(AppServices.getCurrentBlockHeight(), pastWallet);
    }

    public void saveBackup() throws IOException {
        storage.backupWallet();
    }

    protected void backgroundUpdate() {
        try {
            storage.updateWallet(wallet);
        } catch (IOException | StorageException e) {
            //Background save failed
            log.error("Background wallet save failed", e);
        }
    }

    public void deleteBackups() {
        storage.deleteBackups();
    }

    public void refreshHistory(Integer blockHeight, Wallet pastWallet) {
        refreshHistory(blockHeight, pastWallet, null);
    }

    public void refreshHistory(Integer blockHeight, Wallet pastWallet, Set<WalletNode> nodes) {
        Wallet previousWallet = wallet.copy();
        if(wallet.isValid() && AppServices.isConnected()) {
            if(log.isDebugEnabled()) {
                log.debug(nodes == null ? wallet.getFullName() + " refreshing full wallet history" : wallet.getFullName() + " requesting node wallet history for " + nodeRangesToString(nodes));
            }

            ElectrumServer.TransactionHistoryService historyService = new ElectrumServer.TransactionHistoryService(wallet, getWalletTransactionNodes(nodes));
            historyService.setOnSucceeded(workerStateEvent -> {
                if(historyService.getValue()) {
                    EventManager.get().post(new WalletHistoryFinishedEvent(wallet));
                    updateWallet(blockHeight, pastWallet, previousWallet);
                }
            });
            historyService.setOnFailed(workerStateEvent -> {
                if(workerStateEvent.getSource().getException() instanceof AllHistoryChangedException) {
                    try {
                        storage.backupWallet();
                    } catch(IOException e) {
                        log.error("Error backing up wallet", e);
                    }

                    wallet.clearHistory();
                    AppServices.clearTransactionHistoryCache(wallet);
                    EventManager.get().post(new WalletHistoryClearedEvent(wallet, pastWallet, getWalletId()));
                } else {
                    if(AppServices.isConnected()) {
                        log.error("Error retrieving wallet history", workerStateEvent.getSource().getException());
                    } else {
                        log.debug("Disconnected while retrieving wallet history", workerStateEvent.getSource().getException());
                    }

                    EventManager.get().post(new WalletHistoryFailedEvent(wallet, workerStateEvent.getSource().getException()));
                }
            });

            EventManager.get().post(new WalletHistoryStartedEvent(wallet, nodes));
            historyService.start();
        }
    }

    private void updateWallet(Integer blockHeight, Wallet pastWallet, Wallet previousWallet) {
        if(blockHeight != null) {
            wallet.setStoredBlockHeight(blockHeight);
        }

        //After the wallet settings are changed, the previous wallet is copied to pastWallet and used here to copy labels from past nodes, txos and txes
        Set<Entry> labelChangedEntries = Collections.emptySet();
        if(pastWallet != null) {
            labelChangedEntries = copyLabels(pastWallet);
            copyMixData(pastWallet);
        }

        notifyIfChanged(blockHeight, previousWallet, labelChangedEntries);
    }

    private Set<Entry> copyLabels(Wallet pastWallet) {
        Set<Entry> changedEntries = new LinkedHashSet<>();

        //On a full wallet refresh, walletUtxosEntry and walletTransactionsEntry will have no children yet, but AddressesController may have created accountEntries on a walletNodesChangedEvent
        //Copy nodeEntry labels
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            NodeEntry purposeEntry = getNodeEntry(keyPurpose);
            changedEntries.addAll(purposeEntry.copyLabels(pastWallet.getNode(purposeEntry.getNode().getKeyPurpose())));
        }

        //Copy node and txo labels
        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
            if(wallet.getNode(keyPurpose).copyLabels(pastWallet.getNode(keyPurpose))) {
                changedEntries.add(getWalletUtxosEntry());
            }
        }

        //Copy tx labels
        for(Map.Entry<Sha256Hash, BlockTransaction> txEntry : wallet.getTransactions().entrySet()) {
            BlockTransaction pastBlockTransaction = pastWallet.getTransactions().get(txEntry.getKey());
            if(pastBlockTransaction != null && txEntry.getValue() != null && txEntry.getValue().getLabel() == null && pastBlockTransaction.getLabel() != null) {
                txEntry.getValue().setLabel(pastBlockTransaction.getLabel());
                changedEntries.add(getWalletTransactionsEntry());
            }
        }

        //Force saving the backup if the current wallet has fewer transactions than the past wallet (i.e. incomplete load)
        storage.deleteTempBackups(wallet.getTransactions().size() < pastWallet.getTransactions().size());

        return changedEntries;
    }

    private void copyMixData(Wallet pastWallet) {
        wallet.getUtxoMixes().forEach(pastWallet.getUtxoMixes()::putIfAbsent);
    }

    private void notifyIfChanged(Integer blockHeight, Wallet previousWallet, Set<Entry> labelChangedEntries) {
        List<WalletNode> historyChangedNodes = new ArrayList<>();
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.RECEIVE).getChildren(), wallet.getNode(KeyPurpose.RECEIVE).getChildren()));
        historyChangedNodes.addAll(getHistoryChangedNodes(previousWallet.getNode(KeyPurpose.CHANGE).getChildren(), wallet.getNode(KeyPurpose.CHANGE).getChildren()));

        boolean changed = false;
        if(!labelChangedEntries.isEmpty()) {
            List<Entry> eventEntries = labelChangedEntries.stream().filter(entry -> entry != getWalletTransactionsEntry() && entry != getWalletUtxosEntry()).collect(Collectors.toList());
            if(!eventEntries.isEmpty()) {
                Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, eventEntries)));
            }

            changed = true;
        }

        if(!historyChangedNodes.isEmpty()) {
            Platform.runLater(() -> EventManager.get().post(new WalletHistoryChangedEvent(wallet, storage, historyChangedNodes)));
            changed = true;
        }

        if(blockHeight != null && !blockHeight.equals(previousWallet.getStoredBlockHeight())) {
            Platform.runLater(() -> EventManager.get().post(new WalletBlockHeightChangedEvent(wallet, blockHeight)));
            changed = true;
        }

        if(changed) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    private List<WalletNode> getHistoryChangedNodes(Set<WalletNode> previousNodes, Set<WalletNode> currentNodes) {
        List<WalletNode> changedNodes = new ArrayList<>();
        for(WalletNode currentNode : currentNodes) {
            Optional<WalletNode> optPreviousNode = previousNodes.stream().filter(node -> node.equals(currentNode)).findFirst();
            if(optPreviousNode.isPresent()) {
                WalletNode previousNode = optPreviousNode.get();
                if(!currentNode.getTransactionOutputs().equals(previousNode.getTransactionOutputs())) {
                    changedNodes.add(currentNode);
                }
            } else {
                changedNodes.add(currentNode);
            }
        }

        return changedNodes;
    }

    public void addWalletTransactionNodes(Set<WalletNode> transactionNodes) {
        walletTransactionNodes.add(transactionNodes);
    }

    private Set<WalletNode> getWalletTransactionNodes(Set<WalletNode> walletNodes) {
        if(walletNodes == null) {
            return null;
        }

        Set<WalletNode> allNodes = new LinkedHashSet<>();
        for(WalletNode walletNode : walletNodes) {
            for(Set<WalletNode> nodes : walletTransactionNodes) {
                if(nodes.contains(walletNode)) {
                    allNodes.addAll(nodes);
                }
            }
        }

        return allNodes.isEmpty() ? walletNodes : allNodes;
    }

    public WalletTransaction getCreatedWalletTransaction() {
        return createdWalletTransactionProperty.get();
    }

    public void setCreatedWalletTransaction(WalletTransaction createdWalletTransaction) {
        this.createdWalletTransactionProperty.set(createdWalletTransaction);
    }

    public NodeEntry getNodeEntry(KeyPurpose keyPurpose) {
        NodeEntry purposeEntry;
        Optional<NodeEntry> optionalPurposeEntry = accountEntries.stream().filter(entry -> entry.getNode().getKeyPurpose().equals(keyPurpose)).findFirst();
        if(optionalPurposeEntry.isPresent()) {
            purposeEntry = optionalPurposeEntry.get();
        } else {
            WalletNode purposeNode = getWallet().getNode(keyPurpose);
            purposeEntry = new NodeEntry(getWallet(), purposeNode);
            accountEntries.add(purposeEntry);
        }

        return purposeEntry;
    }

    public NodeEntry getFreshNodeEntry(KeyPurpose keyPurpose, NodeEntry currentEntry) {
        NodeEntry rootEntry = getNodeEntry(keyPurpose);
        WalletNode freshNode = getWallet().getFreshNode(keyPurpose, currentEntry == null ? null : currentEntry.getNode());

        for(Entry childEntry : rootEntry.getChildren()) {
            NodeEntry nodeEntry = (NodeEntry)childEntry;
            if(nodeEntry.getNode().equals(freshNode)) {
                return nodeEntry;
            }
        }

        NodeEntry freshEntry = new NodeEntry(getWallet(), freshNode);
        rootEntry.getChildren().add(freshEntry);
        return freshEntry;
    }

    public WalletTransactionsEntry getWalletTransactionsEntry() {
        if(walletTransactionsEntry == null) {
            walletTransactionsEntry = new WalletTransactionsEntry(wallet);
        }

        return walletTransactionsEntry;
    }

    public WalletUtxosEntry getWalletUtxosEntry() {
        if(walletUtxosEntry == null) {
            walletUtxosEntry = new WalletUtxosEntry(wallet);
        }

        return walletUtxosEntry;
    }

    public boolean isLocked() {
        return lockedProperty.get();
    }

    public BooleanProperty lockedProperty() {
        return lockedProperty;
    }

    public void setLocked(boolean locked) {
        this.lockedProperty.set(locked);
    }

    @Subscribe
    public void walletDataChanged(WalletDataChangedEvent event) {
        if(event.getWallet().equals(wallet)) {
            backgroundUpdate();
        }
    }

    @Subscribe
    public void walletHistoryCleared(WalletHistoryClearedEvent event) {
        if(event.getWalletId().equals(getWalletId())) {
            //Replacing the WalletForm's wallet here is only possible because we immediately clear all derived structures and do a full wallet refresh
            wallet = event.getWallet();

            walletTransactionsEntry = null;
            walletUtxosEntry = null;
            accountEntries.clear();
            EventManager.get().post(new WalletNodesChangedEvent(wallet));

            //It is necessary to save the past wallet because the actual copying of the past labels only occurs on a later ConnectionEvent with bwt
            if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                savedPastWallet = event.getPastWallet();
            }

            //Clear the cache - we will need to fetch everything again
            AppServices.clearTransactionHistoryCache(wallet);
            refreshHistory(AppServices.getCurrentBlockHeight(), event.getPastWallet());
        }
    }

    @Subscribe
    public void keystoreLabelsChanged(KeystoreLabelsChangedEvent event) {
        if(event.getWalletId().equals(getWalletId())) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void keystoreEncryptionChanged(KeystoreEncryptionChangedEvent event) {
        if(event.getWalletId().equals(getWalletId())) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletPasswordChanged(WalletPasswordChangedEvent event) {
        if(event.getWalletId().equals(getWalletId())) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void newBlock(NewBlockEvent event) {
        //Check if wallet is valid to avoid saving wallets in initial setup
        if(wallet.isValid()) {
            updateWallet(event.getHeight(), null, wallet.copy());
        }
    }

    @Subscribe
    public void connected(ConnectionEvent event) {
        refreshHistory(event.getBlockHeight(), savedPastWallet);
        savedPastWallet = null;
    }

    @Subscribe
    public void walletNodeHistoryChanged(WalletNodeHistoryChangedEvent event) {
        if(wallet.isValid()) {
            if(transactionMempoolService != null) {
                transactionMempoolService.cancel();
            }

            WalletNode walletNode = event.getWalletNode(wallet);
            if(walletNode != null) {
                log.debug(wallet.getFullName() + " history event for node " + walletNode + " (" + event.getScriptHash() + ")");
                refreshHistory(AppServices.getCurrentBlockHeight(), null, Set.of(walletNode));
            }
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWalletId().equals(getWalletId())) {
            for(WalletNode changedNode : event.getHistoryChangedNodes()) {
                if(changedNode.getLabel() != null && !changedNode.getLabel().isEmpty()) {
                    List<Entry> changedLabelEntries = new ArrayList<>();
                    for(BlockTransactionHashIndex receivedRef : changedNode.getTransactionOutputs()) {
                        BlockTransaction blockTransaction = wallet.getTransactions().get(receivedRef.getHash());
                        if(blockTransaction != null && (blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty())) {
                            blockTransaction.setLabel(changedNode.getLabel());
                            changedLabelEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                        }

                        if((receivedRef.getLabel() == null || receivedRef.getLabel().isEmpty()) && wallet.getStandardAccountType() != StandardAccount.WHIRLPOOL_PREMIX) {
                            receivedRef.setLabel(changedNode.getLabel() + (changedNode.getKeyPurpose() == KeyPurpose.CHANGE ? " (change)" : " (received)"));
                            changedLabelEntries.add(new HashIndexEntry(event.getWallet(), receivedRef, HashIndexEntry.Type.OUTPUT, changedNode.getKeyPurpose()));
                        }
                    }

                    if(!changedLabelEntries.isEmpty()) {
                        Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(event.getWallet(), changedLabelEntries)));
                    }
                }
            }
        }
    }

    @Subscribe
    public void walletLabelsChanged(WalletEntryLabelsChangedEvent event) {
        if(event.getWallet() == wallet) {
            List<Entry> labelChangedEntries = new ArrayList<>();
            for(Entry entry : event.getEntries()) {
                if(entry.getLabel() != null && !entry.getLabel().isEmpty()) {
                    if(entry instanceof TransactionEntry) {
                        TransactionEntry transactionEntry = (TransactionEntry)entry;
                        for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
                            for(WalletNode childNode : wallet.getNode(keyPurpose).getChildren()) {
                                for(BlockTransactionHashIndex receivedRef : childNode.getTransactionOutputs()) {
                                    if(receivedRef.getHash().equals(transactionEntry.getBlockTransaction().getHash())) {
                                        if((receivedRef.getLabel() == null || receivedRef.getLabel().isEmpty()) && wallet.getStandardAccountType() != StandardAccount.WHIRLPOOL_PREMIX) {
                                            receivedRef.setLabel(entry.getLabel() + (keyPurpose == KeyPurpose.CHANGE ? " (change)" : " (received)"));
                                            labelChangedEntries.add(new HashIndexEntry(event.getWallet(), receivedRef, HashIndexEntry.Type.OUTPUT, keyPurpose));
                                        }
                                        if(childNode.getLabel() == null || childNode.getLabel().isEmpty()) {
                                            childNode.setLabel(entry.getLabel());
                                            labelChangedEntries.add(new NodeEntry(event.getWallet(), childNode));
                                        }
                                    }
                                    if(receivedRef.isSpent() && receivedRef.getSpentBy().getHash().equals(transactionEntry.getBlockTransaction().getHash()) && (receivedRef.getSpentBy().getLabel() == null || receivedRef.getSpentBy().getLabel().isEmpty())) {
                                        receivedRef.getSpentBy().setLabel(entry.getLabel() + " (input)");
                                        labelChangedEntries.add(new HashIndexEntry(event.getWallet(), receivedRef.getSpentBy(), HashIndexEntry.Type.INPUT, keyPurpose));
                                    }
                                }
                            }
                        }
                    }
                    if(entry instanceof NodeEntry) {
                        NodeEntry nodeEntry = (NodeEntry)entry;
                        for(BlockTransactionHashIndex receivedRef : nodeEntry.getNode().getTransactionOutputs()) {
                            BlockTransaction blockTransaction = event.getWallet().getTransactions().get(receivedRef.getHash());
                            if(blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty()) {
                                blockTransaction.setLabel(entry.getLabel());
                                labelChangedEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                            }
                        }
                    }
                    if(entry instanceof HashIndexEntry) {
                        HashIndexEntry hashIndexEntry = (HashIndexEntry)entry;
                        BlockTransaction blockTransaction = hashIndexEntry.getBlockTransaction();
                        if(blockTransaction.getLabel() == null || blockTransaction.getLabel().isEmpty()) {
                            blockTransaction.setLabel(entry.getLabel());
                            labelChangedEntries.add(new TransactionEntry(event.getWallet(), blockTransaction, Collections.emptyMap(), Collections.emptyMap()));
                        }
                    }
                }
            }

            if(!labelChangedEntries.isEmpty()) {
                Platform.runLater(() -> EventManager.get().post(new WalletEntryLabelsChangedEvent(wallet, labelChangedEntries)));
            } else {
                Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
            }
        }
    }

    @Subscribe
    public void walletDeleted(WalletDeletedEvent event) {
        if(event.getWallet() == wallet && !wallet.isMasterWallet()) {
            wallet.getMasterWallet().getChildWallets().remove(wallet);
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletUtxoStatusChanged(WalletUtxoStatusChangedEvent event) {
        if(event.getWallet() == wallet) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletMixConfigChanged(WalletMixConfigChangedEvent event) {
        if(event.getWallet() == wallet) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletUtxoMixesChanged(WalletUtxoMixesChangedEvent event) {
        if(event.getWallet() == wallet) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletLabelChanged(WalletLabelChangedEvent event) {
        if(event.getWallet() == wallet) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));
        }
    }

    @Subscribe
    public void walletGapLimitChanged(WalletGapLimitChangedEvent event) {
        if(event.getWallet() == wallet) {
            Platform.runLater(() -> EventManager.get().post(new WalletDataChangedEvent(wallet)));

            Set<WalletNode> newNodes = new LinkedHashSet<>();
            for(KeyPurpose keyPurpose : KeyPurpose.DEFAULT_PURPOSES) {
                Optional<WalletNode> optPurposeNode = wallet.getPurposeNodes().stream().filter(node -> node.getKeyPurpose() == keyPurpose).findFirst();
                if(optPurposeNode.isPresent()) {
                    WalletNode purposeNode = optPurposeNode.get();
                    newNodes.addAll(purposeNode.fillToIndex(wallet.getLookAheadIndex(purposeNode)));
                }
            }

            if(!newNodes.isEmpty()) {
                Platform.runLater(() -> refreshHistory(AppServices.getCurrentBlockHeight(), null, newNodes));
            }
        }
    }

    @Subscribe
    public void whirlpoolMixSuccess(WhirlpoolMixSuccessEvent event) {
        if(event.getWallet() == wallet && event.getWalletNode() != null) {
            if(transactionMempoolService != null) {
                transactionMempoolService.cancel();
            }

            transactionMempoolService = new ElectrumServer.TransactionMempoolService(event.getWallet(), Sha256Hash.wrap(event.getNextUtxo().getHash()), Set.of(event.getWalletNode()));
            transactionMempoolService.setDelay(Duration.seconds(5));
            transactionMempoolService.setPeriod(Duration.seconds(5));
            transactionMempoolService.setRestartOnFailure(false);
            transactionMempoolService.setOnSucceeded(mempoolWorkerStateEvent -> {
                Set<String> scriptHashes = transactionMempoolService.getValue();
                if(!scriptHashes.isEmpty()) {
                    Platform.runLater(() -> EventManager.get().post(new WalletNodeHistoryChangedEvent(scriptHashes.iterator().next())));
                }

                if(transactionMempoolService.getIterationCount() > 10) {
                    transactionMempoolService.cancel();
                }
            });
            transactionMempoolService.start();
        }
    }

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData tabData : event.getClosedWalletTabData()) {
            if(tabData.getWalletForm() == this) {
                if(wallet.isMasterWallet()) {
                    storage.close();
                }
                if(wallet.isValid()) {
                    AppServices.clearTransactionHistoryCache(wallet);
                }
                EventManager.get().unregister(this);
            }
        }
    }

    @Subscribe
    public void hideEmptyUsedAddressesStatusChanged(HideEmptyUsedAddressesStatusEvent event) {
        accountEntries.clear();
        EventManager.get().post(new WalletAddressesStatusEvent(wallet));
    }
}
