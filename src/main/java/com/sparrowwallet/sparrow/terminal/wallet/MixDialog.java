package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;
import com.samourai.whirlpool.client.whirlpool.beans.Pool;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.WalletMasterMixConfigChangedEvent;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.UtxoEntry;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.whirlpool.dataSource.SparrowMinerFeeSupplier;

import java.util.List;
import java.util.Locale;

public class MixDialog extends WalletDialog {
    private static final List<FeePriority> FEE_PRIORITIES = List.of(new FeePriority("Low", Tx0FeeTarget.MIN), new FeePriority("Normal", Tx0FeeTarget.BLOCKS_4), new FeePriority("High", Tx0FeeTarget.BLOCKS_2));

    private final String walletId;
    private final List<UtxoEntry> utxoEntries;

    private final TextBox scode;
    private final ComboBox<FeePriority> premixPriority;
    private final Label premixFeeRate;

    private Pool mixPool;

    public MixDialog(String walletId, WalletForm walletForm, List<UtxoEntry> utxoEntries) {
        super(walletForm.getWallet().getFullDisplayName() + " Premix Config", walletForm);

        this.walletId = walletId;
        this.utxoEntries = utxoEntries;

        setHints(List.of(Hint.CENTERED));

        Wallet wallet = walletForm.getWallet();
        MixConfig mixConfig = wallet.getMasterMixConfig();

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("SCODE"));
        scode = new TextBox(new TerminalSize(20, 1));
        mainPanel.addComponent(scode);

        mainPanel.addComponent(new Label("Premix priority"));
        premixPriority = new ComboBox<>();
        FEE_PRIORITIES.forEach(premixPriority::addItem);
        mainPanel.addComponent(premixPriority);

        mainPanel.addComponent(new Label("Premix fee rate"));
        premixFeeRate = new Label("");
        mainPanel.addComponent(premixFeeRate);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));
        Button next = new Button("Next", this::onNext).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
        buttonPanel.addComponent(next);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        scode.setText(mixConfig.getScode() == null ? "" : mixConfig.getScode());

        premixPriority.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            FeePriority feePriority = premixPriority.getItem(selectedIndex);
            premixFeeRate.setText(SparrowMinerFeeSupplier.getFee(Integer.parseInt(feePriority.getTx0FeeTarget().getFeeTarget().getValue())) + " sats/vB");
        });
        premixPriority.setSelectedIndex(1);

        scode.setTextChangeListener((newText, changedByUserInteraction) -> {
            if(changedByUserInteraction) {
                scode.setText(newText.toUpperCase(Locale.ROOT));
            }

            mixConfig.setScode(newText.toUpperCase(Locale.ROOT));
            EventManager.get().post(new WalletMasterMixConfigChangedEvent(wallet));
        });
    }

    private void onNext() {
        MixPoolDialog mixPoolDialog = new MixPoolDialog(walletId, getWalletForm(), utxoEntries, premixPriority.getSelectedItem().getTx0FeeTarget());
        mixPool = mixPoolDialog.showDialog(SparrowTerminal.get().getGui());
        close();
    }

    private void onCancel() {
        close();
    }

    @Override
    public Pool showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return mixPool;
    }

    private static class FeePriority {
        private final String name;
        private final Tx0FeeTarget tx0FeeTarget;

        public FeePriority(String name, Tx0FeeTarget tx0FeeTarget) {
            this.name = name;
            this.tx0FeeTarget = tx0FeeTarget;
        }

        public Tx0FeeTarget getTx0FeeTarget() {
            return tx0FeeTarget;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
