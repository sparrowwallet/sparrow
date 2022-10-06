package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.drongo.wallet.StandardAccount.WHIRLPOOL_BADBANK;
import static com.sparrowwallet.drongo.wallet.StandardAccount.WHIRLPOOL_PREMIX;

public class MixToDialog extends WalletDialog {
    private static final DisplayWallet NONE_DISPLAY_WALLET = new DisplayWallet(null);

    private MixConfig mixConfig;

    private final ComboBox<DisplayWallet> mixToWallet;
    private final TextBox minimumMixes;
    private final ComboBox<DisplayIndexRange> indexRange;
    private final Button apply;

    public MixToDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Mix To", walletForm);

        setHints(List.of(Hint.CENTERED));

        Wallet wallet = getWalletForm().getWallet();
        this.mixConfig = wallet.getMasterMixConfig().copy();

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Mix to wallet"));
        mixToWallet = new ComboBox<>();
        mainPanel.addComponent(mixToWallet);

        mainPanel.addComponent(new Label("Minimum mixes"));
        minimumMixes = new TextBox().setValidationPattern(Pattern.compile("[0-9]*"));
        mainPanel.addComponent(minimumMixes);

        mainPanel.addComponent(new Label("Postmix index range"));
        indexRange = new ComboBox<>();
        mainPanel.addComponent(indexRange);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));
        apply = new Button("Apply", this::onApply).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
        apply.setEnabled(false);
        buttonPanel.addComponent(apply);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        List<DisplayWallet> allWallets = new ArrayList<>();
        allWallets.add(NONE_DISPLAY_WALLET);
        List<Wallet> destinationWallets = AppServices.get().getOpenWallets().keySet().stream().filter(openWallet -> openWallet.isValid()
                && (openWallet.getScriptType() == ScriptType.P2WPKH || openWallet.getScriptType() == ScriptType.P2WSH)
                && openWallet != wallet && openWallet != wallet.getMasterWallet()
                && (openWallet.getStandardAccountType() == null || !List.of(WHIRLPOOL_PREMIX, WHIRLPOOL_BADBANK).contains(openWallet.getStandardAccountType()))).collect(Collectors.toList());
        allWallets.addAll(destinationWallets.stream().map(DisplayWallet::new).collect(Collectors.toList()));
        allWallets.forEach(mixToWallet::addItem);

        String mixToWalletId = null;
        try {
            mixToWalletId = AppServices.getWhirlpoolServices().getWhirlpoolMixToWalletId(mixConfig);
        } catch(NoSuchElementException e) {
            //ignore, mix to wallet is not open
        }

        if(mixToWalletId != null) {
            mixToWallet.setSelectedItem(new DisplayWallet(AppServices.get().getWallet(mixToWalletId)));
        } else {
            mixToWallet.setSelectedItem(NONE_DISPLAY_WALLET);
        }

        int initialMinMixes = mixConfig.getMinMixes() == null ? Whirlpool.DEFAULT_MIXTO_MIN_MIXES : mixConfig.getMinMixes();
        minimumMixes.setText(Integer.toString(initialMinMixes));

        List<DisplayIndexRange> indexRanges = Arrays.stream(IndexRange.values()).map(DisplayIndexRange::new).collect(Collectors.toList());
        indexRanges.forEach(indexRange::addItem);

        indexRange.setSelectedItem(new DisplayIndexRange(IndexRange.FULL));
        if(mixConfig.getIndexRange() != null) {
            try {
                indexRange.setSelectedItem(new DisplayIndexRange(IndexRange.valueOf(mixConfig.getIndexRange())));
            } catch(Exception e) {
                //ignore
            }
        }

        mixToWallet.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            DisplayWallet newValue = mixToWallet.getSelectedItem();
            if(newValue == NONE_DISPLAY_WALLET) {
                mixConfig.setMixToWalletName(null);
                mixConfig.setMixToWalletFile(null);
            } else {
                mixConfig.setMixToWalletName(newValue.getWallet().getName());
                mixConfig.setMixToWalletFile(AppServices.get().getOpenWallets().get(newValue.getWallet()).getWalletFile());
            }
            apply.setEnabled(apply.isEnabled() || selectedIndex != previousSelection);
        });

        minimumMixes.setTextChangeListener((newText, changedByUserInteraction) -> {
            try {
                int newValue = Integer.parseInt(newText);
                if(newValue < 2 || newValue > 10000) {
                    return;
                }

                mixConfig.setMinMixes(newValue);
                apply.setEnabled(true);
            } catch(NumberFormatException e) {
                return;
            }
        });

        indexRange.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            DisplayIndexRange newValue = indexRange.getSelectedItem();
            mixConfig.setIndexRange(newValue.getIndexRange().toString());
            apply.setEnabled(apply.isEnabled() || selectedIndex != previousSelection);
        });
    }

    private void onCancel() {
        mixConfig = null;
        close();
    }

    private void onApply() {
        close();
    }

    @Override
    public Object showDialog(WindowBasedTextGUI textGUI) {
        super.showDialog(textGUI);
        return mixConfig;
    }

    private static class DisplayWallet {
        private final Wallet wallet;

        public DisplayWallet(Wallet wallet) {
            this.wallet = wallet;
        }

        public Wallet getWallet() {
            return wallet;
        }

        @Override
        public String toString() {
            return wallet == null ? "None" : wallet.getFullDisplayName();
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            DisplayWallet that = (DisplayWallet) o;
            return Objects.equals(wallet, that.wallet);
        }

        @Override
        public int hashCode() {
            return wallet != null ? wallet.hashCode() : 0;
        }
    }

    private static class DisplayIndexRange {
        private final IndexRange indexRange;

        public DisplayIndexRange(IndexRange indexRange) {
            this.indexRange = indexRange;
        }

        public IndexRange getIndexRange() {
            return indexRange;
        }

        @Override
        public String toString() {
            return indexRange.toString().charAt(0) + indexRange.toString().substring(1).toLowerCase(Locale.ROOT);
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            DisplayIndexRange that = (DisplayIndexRange) o;
            return indexRange == that.indexRange;
        }

        @Override
        public int hashCode() {
            return indexRange.hashCode();
        }
    }
}
