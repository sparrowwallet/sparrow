package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.gui2.*;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.NodeEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Set;

public class ReceiveDialog extends WalletDialog {
    private static final Logger log = LoggerFactory.getLogger(ReceiveDialog.class);
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final Label address;
    private final Label derivation;
    private final Label lastUsed;

    private NodeEntry currentEntry;

    public ReceiveDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Receive", walletForm);

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel(new GridLayout(2).setHorizontalSpacing(2).setVerticalSpacing(1).setTopMarginSize(1));

        mainPanel.addComponent(new Label("Address"));
        address = new Label("").addTo(mainPanel);

        mainPanel.addComponent(new Label("Derivation"));
        derivation = new Label("").addTo(mainPanel);

        mainPanel.addComponent(new Label("Last Used"));
        lastUsed = new Label("").addTo(mainPanel);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Back", () -> onBack(Function.RECEIVE)));
        buttonPanel.addComponent(new Button("Get Fresh Address", this::refreshAddress).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));

        mainPanel.addComponent(new Button("Show QR", this::showQR));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        refreshAddress();
    }

    public void showQR() {
        if(currentEntry == null) {
            return;
        }

        try {
            QRCodeDialog qrCodeDialog = new QRCodeDialog(currentEntry.getAddress().toString());
            qrCodeDialog.showDialog(SparrowTerminal.get().getGui());
        } catch(Exception e) {
            log.error("Error creating QR", e);
            AppServices.showErrorDialog("Error creating QR", e.getMessage());
        }
    }

    public void refreshAddress() {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            NodeEntry freshEntry = getWalletForm().getFreshNodeEntry(KeyPurpose.RECEIVE, currentEntry);
            setNodeEntry(freshEntry);
        });
    }

    public void setNodeEntry(NodeEntry nodeEntry) {
        this.currentEntry = nodeEntry;
        address.setText(nodeEntry.getAddress().toString());
        derivation.setText(getDerivationPath(nodeEntry.getNode()));
        updateLastUsed();
    }

    protected String getDerivationPath(WalletNode node) {
        if(isSingleDerivationPath()) {
            KeyDerivation firstDerivation = getWalletForm().getWallet().getKeystores().get(0).getKeyDerivation();
            return firstDerivation.extend(node.getDerivation()).getDerivationPath();
        }

        return node.getDerivationPath().replace("m", "multi");
    }

    protected boolean isSingleDerivationPath() {
        KeyDerivation firstDerivation = getWalletForm().getWallet().getKeystores().get(0).getKeyDerivation();
        for(Keystore keystore : getWalletForm().getWallet().getKeystores()) {
            if(!keystore.getKeyDerivation().getDerivationPath().equals(firstDerivation.getDerivationPath())) {
                return false;
            }
        }

        return true;
    }

    private void updateLastUsed() {
        SparrowTerminal.get().getGuiThread().invokeLater(() -> {
            Set<BlockTransactionHashIndex> currentOutputs = currentEntry.getNode().getTransactionOutputs();
            if(AppServices.onlineProperty().get() && currentOutputs.isEmpty()) {
                lastUsed.setText("Never");
            } else if(!currentOutputs.isEmpty()) {
                long count = currentOutputs.size();
                BlockTransactionHashIndex lastUsedReference = currentOutputs.stream().skip(count - 1).findFirst().get();
                lastUsed.setText(lastUsedReference.getHeight() <= 0 ? "Unconfirmed Transaction" : (lastUsedReference.getDate() == null ? "Unknown" : DATE_FORMAT.format(lastUsedReference.getDate())));
            } else {
                lastUsed.setText("Unknown");
            }
        });
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            if(currentEntry != null) {
                currentEntry = null;
            }
            refreshAddress();
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(getWalletForm().getWallet())) {
            if(currentEntry != null && event.getHistoryChangedNodes().contains(currentEntry.getNode())) {
                refreshAddress();
            }
        }
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        updateLastUsed();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        updateLastUsed();
    }
}
