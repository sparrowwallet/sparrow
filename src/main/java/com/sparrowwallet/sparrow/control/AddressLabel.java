package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class AddressLabel extends IdLabel {
    private final ObjectProperty<Address> addressProperty = new SimpleObjectProperty<>();

    private Tooltip tooltip;
    private AddressContextMenu contextMenu;

    public AddressLabel() {
        this("");
    }

    public AddressLabel(String text) {
        super(text);
        addressProperty().addListener((observable, oldValue, newValue) -> {
            setAddressAsText(newValue);
            contextMenu.copyHex.setText("Copy " + newValue.getOutputScriptDataType());
        });

        tooltip = new Tooltip();
        contextMenu = new AddressContextMenu();
    }

    public final ObjectProperty<Address> addressProperty() {
        return addressProperty;
    }

    public final Address getAddress() {
        return addressProperty.get();
    }

    public final void setAddress(Address value) {
        this.addressProperty.set(value);
    }

    private void setAddressAsText(Address address) {
        if(address != null) {
            tooltip.setText(address.getScriptType() + " " + Utils.bytesToHex(address.getOutputScriptData()));
            setTooltip(tooltip);
            setContextMenu(contextMenu);
            setText(address.toString());
        }
    }

    private class AddressContextMenu extends ContextMenu {
        private MenuItem copyAddress;
        private MenuItem copyHex;

        public AddressContextMenu() {
            copyAddress = new MenuItem("Copy Address");
            copyAddress.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(getAddress().toString());
                Clipboard.getSystemClipboard().setContent(content);
            });

            copyHex = new MenuItem("Copy Script Output Bytes");
            copyHex.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(Utils.bytesToHex(getAddress().getOutputScriptData()));
                Clipboard.getSystemClipboard().setContent(content);
            });

            getItems().addAll(copyAddress, copyHex);
        }
    }
}
