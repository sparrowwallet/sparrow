package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ServerType;
import com.sparrowwallet.sparrow.whirlpool.WhirlpoolServices;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.glyphfont.Glyph;

import java.util.ArrayList;
import java.util.List;

import static com.sparrowwallet.drongo.wallet.StandardAccount.*;

public class AddAccountDialog extends Dialog<List<StandardAccount>> {
    private static final int MAX_SHOWN_ACCOUNTS = 8;

    private final ComboBox<StandardAccount> standardAccountCombo;
    private boolean discoverAccounts = false;

    public AddAccountDialog(Wallet wallet) {
        final DialogPane dialogPane = getDialogPane();
        setTitle("Add Account");
        dialogPane.setHeaderText("Choose an account to add:");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogPane.setPrefWidth(380);
        dialogPane.setPrefHeight(200);
        AppServices.moveToActiveWindowScreen(this);

        Glyph key = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.SORT_NUMERIC_DOWN);
        key.setFontSize(50);
        dialogPane.setGraphic(key);

        final VBox content = new VBox(10);
        content.setPrefHeight(50);

        standardAccountCombo = new ComboBox<>();
        standardAccountCombo.setMaxWidth(Double.MAX_VALUE);

        List<Integer> existingIndexes = new ArrayList<>();
        Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
        existingIndexes.add(masterWallet.getAccountIndex());
        for(Wallet childWallet : masterWallet.getChildWallets()) {
            if(!childWallet.isNested()) {
                existingIndexes.add(childWallet.getAccountIndex());
            }
        }

        List<StandardAccount> availableAccounts = new ArrayList<>();
        for(StandardAccount standardAccount : StandardAccount.values()) {
            if(!existingIndexes.contains(standardAccount.getAccountNumber()) && !StandardAccount.isWhirlpoolAccount(standardAccount) && availableAccounts.size() <= MAX_SHOWN_ACCOUNTS) {
                availableAccounts.add(standardAccount);
            }
        }

        if(WhirlpoolServices.canWalletMix(masterWallet) && !masterWallet.isWhirlpoolMasterWallet()) {
            availableAccounts.add(WHIRLPOOL_PREMIX);
        } else if(WhirlpoolServices.canWatchPostmix(masterWallet) && !existingIndexes.contains(WHIRLPOOL_POSTMIX.getAccountNumber())) {
            availableAccounts.add(WHIRLPOOL_POSTMIX);
        }

        final ButtonType discoverButtonType = new javafx.scene.control.ButtonType("Discover", ButtonBar.ButtonData.LEFT);
        if(!availableAccounts.isEmpty() && Config.get().getServerType() != ServerType.BITCOIN_CORE &&
                (masterWallet.getKeystores().stream().allMatch(ks -> ks.getSource() == KeystoreSource.SW_SEED)
                        || (masterWallet.getKeystores().size() == 1 && masterWallet.getKeystores().stream().allMatch(ks -> ks.getSource() == KeystoreSource.HW_USB)))) {
            dialogPane.getButtonTypes().add(discoverButtonType);
            Button discoverButton = (Button)dialogPane.lookupButton(discoverButtonType);
            discoverButton.disableProperty().bind(AppServices.onlineProperty().not());
            discoverButton.setOnAction(event -> {
                discoverAccounts = true;
            });
        }

        standardAccountCombo.setItems(FXCollections.observableList(availableAccounts));
        standardAccountCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(StandardAccount account) {
                if(account == null) {
                    return "None Available";
                }

                if(account == WHIRLPOOL_PREMIX) {
                    return "Whirlpool Accounts";
                }

                if(account == WHIRLPOOL_POSTMIX) {
                    return "Whirlpool Postmix (No mixing)";
                }

                return account.getName();
            }

            @Override
            public StandardAccount fromString(String string) {
                return null;
            }
        });

        if(standardAccountCombo.getItems().isEmpty()) {
            Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
            okButton.setDisable(true);
        } else {
            standardAccountCombo.getSelectionModel().select(0);
        }
        content.getChildren().add(standardAccountCombo);

        dialogPane.setContent(content);
        setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? List.of(standardAccountCombo.getValue()) : (dialogButton == discoverButtonType ? availableAccounts : null));
    }

    public boolean isDiscoverAccounts() {
        return discoverAccounts;
    }
}
