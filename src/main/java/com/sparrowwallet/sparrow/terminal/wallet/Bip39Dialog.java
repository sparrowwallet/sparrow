package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.Bip39;
import com.sparrowwallet.sparrow.io.ImportException;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Bip39Dialog extends NewWalletDialog {
    private static final Logger log = LoggerFactory.getLogger(Bip39Dialog.class);
    public static final int MAX_COLUMNS = 40;

    private final Bip39 importer = new Bip39();

    private final ComboBox<DisplayScriptType> scriptType;
    private final TextBox seedWords;
    private final TextBox passphrase;
    private final Button createWallet;

    public Bip39Dialog(String walletName) {
        super("Create BIP39 Wallet - " + walletName, walletName);

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(new Label("Script type"));
        scriptType = new ComboBox<>();
        mainPanel.addComponent(scriptType);

        mainPanel.addComponent(new Label("Seed words"));
        seedWords = new TextBox(new TerminalSize(MAX_COLUMNS, 5));
        mainPanel.addComponent(seedWords);

        mainPanel.addComponent(new Label("Passphrase"));
        passphrase = new TextBox(new TerminalSize(25, 1));
        mainPanel.addComponent(passphrase);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Cancel", this::onCancel));
        createWallet = new Button("Create Wallet", this::createWallet).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
        createWallet.setEnabled(false);
        buttonPanel.addComponent(createWallet);

        mainPanel.addComponent(new Button("Generate New", () -> generateNew()));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);

        ScriptType.getAddressableScriptTypes(PolicyType.SINGLE).stream().map(DisplayScriptType::new).forEach(scriptType::addItem);
        scriptType.setSelectedItem(new DisplayScriptType(ScriptType.P2WPKH));

        seedWords.setTextChangeListener((newText, changedByUserInteraction) -> {
            try {
                String[] words = newText.split("[ \n]");
                importer.getKeystore(scriptType.getSelectedItem().scriptType.getDefaultDerivation(), Arrays.asList(words), passphrase.getText());
                createWallet.setEnabled(true);
            } catch(ImportException e) {
                createWallet.setEnabled(false);
            }

            if(changedByUserInteraction) {
                List<String> lines = splitString(newText, MAX_COLUMNS);
                String splitText = lines.stream().reduce((s1, s2) -> s1 + "\n" + s2).get();
                if(!newText.equals(splitText)) {
                    seedWords.setText(splitText);

                    TerminalPosition pos = seedWords.getCaretPosition();
                    if(pos.getRow() == lines.size() - 2 && pos.getColumn() == lines.get(lines.size() - 2).length()) {
                        seedWords.setCaretPosition(lines.size() - 1, lines.get(lines.size() - 1).length());
                    }
                }
            }
        });
    }

    private void generateNew() {
        WordNumberDialog wordNumberDialog = new WordNumberDialog();
        Integer numberOfWords = wordNumberDialog.showDialog(SparrowTerminal.get().getGui());

        if(numberOfWords != null) {
            int mnemonicSeedLength = numberOfWords * 11;
            int entropyLength = mnemonicSeedLength - (mnemonicSeedLength/33);

            SecureRandom secureRandom;
            try {
                secureRandom = SecureRandom.getInstanceStrong();
            } catch(NoSuchAlgorithmException e) {
                secureRandom = new SecureRandom();
            }

            DeterministicSeed deterministicSeed = new DeterministicSeed(secureRandom, entropyLength, "");
            List<String> words = deterministicSeed.getMnemonicCode();
            setWords(words);
        }
    }

    private List<String> getWords() {
        return List.of(seedWords.getText().split("[ \n]"));
    }

    private void setWords(List<String> words) {
        String text = words.stream().reduce((s1, s2) -> s1 + " " + s2).get();
        List<String> splitText = splitString(text, MAX_COLUMNS);
        seedWords.setText(splitText.stream().reduce((s1, s2) -> s1 + "\n" + s2).get());
    }

    public static List<String> splitString(String stringToSplit, int maxLength) {
        int splitLength = maxLength - 1;
        String text = stringToSplit;
        List<String> lines = new ArrayList<>();
        while (text.length() > splitLength) {
            int spaceAt = splitLength - 1;
            // the text is too long.
            // find the last space before the maxLength
            for (int i = splitLength - 1; i > 0; i--) {
                if (Character.isWhitespace(text.charAt(i))) {
                    spaceAt = i;
                    break;
                }
            }
            lines.add(text.substring(0, spaceAt));
            text = text.substring(spaceAt + 1);
        }
        lines.add(text);
        return lines;
    }

    @Override
    protected List<Wallet> getWallets() throws ImportException {
        Wallet wallet = new Wallet(walletName);
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(scriptType.getSelectedItem().scriptType);
        Keystore keystore = importer.getKeystore(wallet.getScriptType().getDefaultDerivation(), getWords(), passphrase.getText());
        wallet.getKeystores().add(keystore);
        wallet.setDefaultPolicy(Policy.getPolicy(PolicyType.SINGLE, wallet.getScriptType(), wallet.getKeystores(), 1));
        return List.of(wallet);
    }

    private static final class DisplayScriptType {
        private final ScriptType scriptType;

        public DisplayScriptType(ScriptType scriptType) {
            this.scriptType = scriptType;
        }

        @Override
        public String toString() {
            return scriptType.getDescription();
        }

        @Override
        public boolean equals(Object o) {
            if(this == o) {
                return true;
            }
            if(o == null || getClass() != o.getClass()) {
                return false;
            }

            DisplayScriptType that = (DisplayScriptType) o;

            return scriptType == that.scriptType;
        }

        @Override
        public int hashCode() {
            return scriptType.hashCode();
        }
    }

    private static final class WordNumberDialog extends DialogWindow {
        private final ComboBox<Integer> wordCount;
        private Integer numberOfWords;

        public WordNumberDialog() {
            super("Generate Seed Words");

            setHints(List.of(Hint.CENTERED));

            Panel mainPanel = new Panel();
            mainPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(5).setVerticalSpacing(1));

            mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
            mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

            mainPanel.addComponent(new Label("Number of words"));
            wordCount = new ComboBox<>();
            mainPanel.addComponent(wordCount);

            wordCount.addItem(24);
            wordCount.addItem(21);
            wordCount.addItem(18);
            wordCount.addItem(15);
            wordCount.addItem(12);

            Panel buttonPanel = new Panel();
            buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
            buttonPanel.addComponent(new Button("Cancel", this::onCancel));
            Button okButton = new Button("Ok", this::onOk).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false));
            buttonPanel.addComponent(okButton);

            mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

            buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
            setComponent(mainPanel);
        }

        private void onOk() {
            numberOfWords = wordCount.getSelectedItem();
            close();
        }

        private void onCancel() {
            close();
        }

        @Override
        public Integer showDialog(WindowBasedTextGUI textGUI) {
            super.showDialog(textGUI);
            return numberOfWords;
        }
    }
}
