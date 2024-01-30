package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.RequestOpenWalletsEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.controlsfx.glyphfont.Glyph;

import java.util.List;

public class UsbStatusButton extends MenuButton {
    public UsbStatusButton() {
        super("");
        setGraphic(getIcon());

        getStyleClass().add("status-bar-button");
        this.setPopupSide(Side.RIGHT);
    }

    public void setDevices(List<Device> devices) {
        for(Device device : devices) {
            MenuItem deviceItem = new MenuItem(device.getModel().toDisplayString());
            if(!device.isNeedsPinSent() && (device.getModel() == WalletModel.TREZOR_1 || device.getModel() == WalletModel.TREZOR_T ||
                    device.getModel() == WalletModel.TREZOR_SAFE_3 || device.getModel() == WalletModel.KEEPKEY || device.getModel() == WalletModel.BITBOX_02)) {
                deviceItem = new Menu(device.getModel().toDisplayString());
                MenuItem toggleItem = new MenuItem("Toggle Passphrase" + (!device.getModel().externalPassphraseEntry() ? "" : (device.isNeedsPassphraseSent() ? " Off" : " On")));
                toggleItem.setOnAction(event -> {
                    Hwi.TogglePassphraseService togglePassphraseService = new Hwi.TogglePassphraseService(device);
                    togglePassphraseService.setOnSucceeded(event1 -> {
                        EventManager.get().post(new RequestOpenWalletsEvent());
                        if(!device.getModel().externalPassphraseEntry()) {
                            AppServices.showAlertDialog("Reconnect device", "Reconnect your " + device.getModel().toDisplayString() + " to reset the passphrase.", Alert.AlertType.INFORMATION);
                        }
                    });
                    togglePassphraseService.setOnFailed(event1 -> {
                        AppServices.showErrorDialog("Error toggling passphrase", event1.getSource().getException().getMessage());
                    });
                    togglePassphraseService.start();
                });
                ((Menu)deviceItem).getItems().add(toggleItem);
            }

            if(device.isNeedsPinSent()) {
                deviceItem.setGraphic(getLockIcon());
            } else {
                deviceItem.setGraphic(getLockOpenIcon());
            }
            getItems().add(deviceItem);
        }
    }

    private Node getIcon() {
        Glyph usb = new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
        usb.setFontSize(15);
        return usb;
    }

    private Node getLockIcon() {
        Glyph usb = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LOCK);
        usb.setFontSize(12);
        return usb;
    }

    private Node getLockOpenIcon() {
        Glyph usb = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.LOCK_OPEN);
        usb.setFontSize(12);
        return usb;
    }
}
