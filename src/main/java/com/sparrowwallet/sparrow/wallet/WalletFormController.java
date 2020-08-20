package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.sparrowwallet.sparrow.BaseController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.WalletTabsClosedEvent;

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
            } else if(walletForm instanceof SettingsWalletForm && tabData.getStorage() == walletForm.getStorage()) {
                EventManager.get().unregister(this);
            }
        }
    }
}
