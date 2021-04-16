package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
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
