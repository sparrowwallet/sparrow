package com.sparrowwallet.sparrow.wallet;

import com.samourai.whirlpool.client.wallet.beans.IndexRange;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.MixConfig;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.IntegerSpinner;
import com.sparrowwallet.sparrow.event.MixToConfigChangedEvent;
import com.sparrowwallet.sparrow.whirlpool.Whirlpool;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.drongo.wallet.StandardAccount.*;

public class MixToController implements Initializable {
    private static final Wallet NONE_WALLET = new Wallet("None");

    @FXML
    private ComboBox<Wallet> mixToWallets;

    @FXML
    private IntegerSpinner minMixes;

    @FXML
    private ComboBox<IndexRange> indexRange;

    private MixConfig mixConfig;

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    public void initializeView(Wallet wallet) {
        mixConfig = wallet.getMasterMixConfig().copy();

        List<Wallet> allWallets = new ArrayList<>();
        allWallets.add(NONE_WALLET);

        List<Wallet> destinationWallets = AppServices.get().getOpenWallets().keySet().stream().filter(openWallet -> openWallet.isValid()
                && (openWallet.getScriptType() == ScriptType.P2WPKH || openWallet.getScriptType() == ScriptType.P2WSH)
                && openWallet != wallet && openWallet != wallet.getMasterWallet()
                && (openWallet.getStandardAccountType() == null || !List.of(WHIRLPOOL_PREMIX, WHIRLPOOL_BADBANK).contains(openWallet.getStandardAccountType()))).collect(Collectors.toList());
        allWallets.addAll(destinationWallets);

        mixToWallets.setItems(FXCollections.observableList(allWallets));
        mixToWallets.setConverter(new StringConverter<>() {
            @Override
            public String toString(Wallet wallet) {
                return wallet == null ? "" : wallet.getFullDisplayName();
            }

            @Override
            public Wallet fromString(String string) {
                return null;
            }
        });

        String mixToWalletId = null;
        try {
            mixToWalletId = AppServices.getWhirlpoolServices().getWhirlpoolMixToWalletId(mixConfig);
        } catch(NoSuchElementException e) {
            //ignore, mix to wallet is not open
        }

        if(mixToWalletId != null) {
            mixToWallets.setValue(AppServices.get().getWallet(mixToWalletId));
        } else {
            mixToWallets.setValue(NONE_WALLET);
        }

        mixToWallets.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == NONE_WALLET) {
                mixConfig.setMixToWalletName(null);
                mixConfig.setMixToWalletFile(null);
            } else {
                mixConfig.setMixToWalletName(newValue.getName());
                mixConfig.setMixToWalletFile(AppServices.get().getOpenWallets().get(newValue).getWalletFile());
            }

            EventManager.get().post(new MixToConfigChangedEvent(wallet));
        });

        int initialMinMixes = mixConfig.getMinMixes() == null ? Whirlpool.DEFAULT_MIXTO_MIN_MIXES : mixConfig.getMinMixes();
        minMixes.setValueFactory(new IntegerSpinner.ValueFactory(2, 10000, initialMinMixes));
        minMixes.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue == null || newValue < 2 || newValue > 10000) {
                return;
            }

            mixConfig.setMinMixes(newValue);
            EventManager.get().post(new MixToConfigChangedEvent(wallet));
        });

        indexRange.setConverter(new StringConverter<>() {
            @Override
            public String toString(IndexRange indexRange) {
                if(indexRange == null) {
                    return "";
                }

                return indexRange.toString().charAt(0) + indexRange.toString().substring(1).toLowerCase(Locale.ROOT);
            }

            @Override
            public IndexRange fromString(String string) {
                return null;
            }
        });

        indexRange.setValue(IndexRange.FULL);
        if(mixConfig.getIndexRange() != null) {
            try {
                indexRange.setValue(IndexRange.valueOf(mixConfig.getIndexRange()));
            } catch(Exception e) {
                //ignore
            }
        }
        indexRange.valueProperty().addListener((observable, oldValue, newValue) -> {
            mixConfig.setIndexRange(newValue.toString());
            EventManager.get().post(new MixToConfigChangedEvent(wallet));
        });
    }

    public void close() {
        minMixes.commitValue();
    }

    public MixConfig getMixConfig() {
        return mixConfig;
    }
}
