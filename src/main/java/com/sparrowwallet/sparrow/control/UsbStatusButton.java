package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Device;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import org.controlsfx.glyphfont.Glyph;

import java.util.List;

public class UsbStatusButton extends MenuButton {
    private final List<Device> devices;

    public UsbStatusButton(List<Device> devices) {
        super("");
        setGraphic(getIcon());
        this.devices = devices;

        this.setPopupSide(Side.TOP);

        for(Device device : devices) {
            MenuItem deviceItem = new MenuItem(device.getModel().toDisplayString());
            getItems().add(deviceItem);
        }
    }

    private Node getIcon() {
        Glyph usb = new Glyph(FontAwesome5Brands.FONT_NAME, FontAwesome5Brands.Glyph.USB);
        usb.setFontSize(10);
        return usb;
    }

    private class UsbStatusContextMenu extends ContextMenu {
        public UsbStatusContextMenu() {
            for(Device device : devices) {
                MenuItem deviceItem = new MenuItem(device.getModel().toDisplayString());
                getItems().add(deviceItem);
            }
        }
    }
}
