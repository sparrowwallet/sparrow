package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.WalletModel;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.List;

public class MnemonicKeystoreEntryPane extends MnemonicKeystorePane {
    private final BooleanProperty validProperty = new SimpleBooleanProperty(false);

    private boolean generated;

    public MnemonicKeystoreEntryPane(String name, int numWords) {
        super(name, "Enter seed words", "", "image/" + WalletModel.SEED.getType() + ".png");
        showHideLink.setVisible(false);
        buttonBox.getChildren().clear();

        defaultWordSizeProperty.set(numWords);
        setDescription("Generate new or enter existing");
        showHideLink.setVisible(false);
        setContent(getMnemonicWordsEntry(numWords, false, true));
        setExpanded(true);
    }

    @Override
    protected List<Node> createRightButtons() {
        Button button = new Button("Next");
        button.setVisible(false);
        return List.of(button);
    }

    @Override
    protected void onWordChange(boolean empty, boolean validWords, boolean validChecksum) {
        if(!empty && validWords) {
            try {
                Bip39MnemonicCode.INSTANCE.check(wordEntriesProperty.get());
                validChecksum = true;
            } catch(MnemonicException e) {
                invalidLabel.setText("Invalid checksum");
                invalidLabel.setTooltip(null);
            }
        }

        validProperty.set(validChecksum);
        validLabel.setVisible(validChecksum);
        invalidLabel.setVisible(!validChecksum && !empty);
    }

    public void generateNew() {
        int mnemonicSeedLength = wordEntriesProperty.get().size() * 11;
        int entropyLength = mnemonicSeedLength - (mnemonicSeedLength/33);

        SecureRandom secureRandom;
        try {
            secureRandom = SecureRandom.getInstanceStrong();
        } catch(NoSuchAlgorithmException e) {
            secureRandom = new SecureRandom();
        }

        DeterministicSeed deterministicSeed = new DeterministicSeed(secureRandom, entropyLength, "");
        displayMnemonicCode(deterministicSeed);
        generated = true;
    }

    private void displayMnemonicCode(DeterministicSeed deterministicSeed) {
        setDescription("Write down these words");
        showHideLink.setVisible(false);

        for (int i = 0; i < wordsPane.getChildren().size(); i++) {
            WordEntry wordEntry = (WordEntry)wordsPane.getChildren().get(i);
            wordEntry.getEditor().setText(deterministicSeed.getMnemonicCode().get(i));
            wordEntry.getEditor().setEditable(false);
        }
    }

    public boolean isValid() {
        return validProperty.get();
    }

    public BooleanProperty validProperty() {
        return validProperty;
    }

    public boolean isGenerated() {
        return generated;
    }
}
