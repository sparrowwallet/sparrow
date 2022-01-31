package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletNode;
import com.sparrowwallet.sparrow.BaseController;
import com.sparrowwallet.sparrow.CurrencyRate;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.control.FiatLabel;
import com.sparrowwallet.sparrow.event.WalletTabsClosedEvent;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;

public abstract class WalletFormController extends BaseController {
    public WalletForm walletForm;

    public WalletForm getWalletForm() {
        return walletForm;
    }

    public void setWalletForm(WalletForm walletForm) {
        this.walletForm = walletForm;
        initializeView();
    }

    public abstract void initializeView();

    @Subscribe
    public void walletTabsClosed(WalletTabsClosedEvent event) {
        for(WalletTabData tabData : event.getClosedWalletTabData()) {
            if(tabData.getWalletForm() == walletForm) {
                EventManager.get().unregister(this);
            } else if(walletForm instanceof SettingsWalletForm && tabData.getStorage().getWalletId(tabData.getWallet()).equals(walletForm.getWalletId())) {
                EventManager.get().unregister(this);
            }
        }
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

    protected String getDerivationPath(WalletNode node) {
        if(isSingleDerivationPath()) {
            KeyDerivation firstDerivation = getWalletForm().getWallet().getKeystores().get(0).getKeyDerivation();
            return firstDerivation.extend(node.getDerivation()).getDerivationPath();
        }

        return node.getDerivationPath().replace("m", "multi");
    }

    protected void setFiatBalance(FiatLabel fiatLabel, CurrencyRate currencyRate, long balance) {
        if(currencyRate != null && currencyRate.isAvailable() && balance > 0) {
            fiatLabel.set(currencyRate, balance);
        } else {
            fiatLabel.setCurrency(null);
            fiatLabel.setBtcRate(0.0);
        }
    }

    protected boolean selectEntry(TreeTableView<Entry> treeTableView, TreeItem<Entry> parentEntry, Entry entry) {
        for(TreeItem<Entry> treeEntry : parentEntry.getChildren()) {
            if(treeEntry.getValue().equals(entry)) {
                Platform.runLater(() -> {
                    treeTableView.requestFocus();
                    treeTableView.getSelectionModel().select(treeEntry);
                    treeTableView.scrollTo(treeTableView.getSelectionModel().getSelectedIndex());
                });
                return true;
            }

            boolean selectedChild = selectEntry(treeTableView, treeEntry, entry);
            if(selectedChild) {
                treeEntry.setExpanded(true);
                return true;
            }
        }

        return false;
    }
}
