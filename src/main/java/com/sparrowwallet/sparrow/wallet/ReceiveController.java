package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.Glyph;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ReceiveController extends WalletFormController implements Initializable {
    private static final Logger log = LoggerFactory.getLogger(ReceiveController.class);

    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    @FXML
    private CopyableTextField address;

    @FXML
    private TextField label;

    @FXML
    private CopyableLabel derivationPath;

    @FXML
    private Label lastUsed;

    @FXML
    private ImageView qrCode;

    @FXML
    private ScriptArea scriptPubKeyArea;

    @FXML
    private CodeArea outputDescriptor;

    @FXML
    private Button displayAddress;

    private NodeEntry currentEntry;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        initializeScriptField(scriptPubKeyArea);

        displayAddress.managedProperty().bind(displayAddress.visibleProperty());
        displayAddress.setVisible(false);

        qrCode.setOnMouseClicked(event -> {
            if(currentEntry != null) {
                QRDisplayDialog qrDisplayDialog = new QRDisplayDialog(currentEntry.getAddress().toString());
                qrDisplayDialog.showAndWait();
            }
        });

        refreshAddress();
    }

    public void setNodeEntry(NodeEntry nodeEntry) {
        if(currentEntry != null) {
            label.textProperty().unbindBidirectional(currentEntry.labelProperty());
        }

        this.currentEntry = nodeEntry;
        address.setText(nodeEntry.getAddress().toString());
        label.textProperty().bindBidirectional(nodeEntry.labelProperty());
        updateDerivationPath(nodeEntry);

        updateLastUsed();

        Image qrImage = getQrCode(nodeEntry.getAddress().toString());
        if(qrImage != null) {
            qrCode.setImage(qrImage);
        }

        scriptPubKeyArea.clear();
        scriptPubKeyArea.appendScript(nodeEntry.getOutputScript(), null, null);

        outputDescriptor.clear();
        outputDescriptor.append(nodeEntry.getOutputDescriptor(), "descriptor-text");

        updateDisplayAddress(AppServices.getDevices());
    }

    private void updateDerivationPath(NodeEntry nodeEntry) {
        derivationPath.setText(getDerivationPath(nodeEntry.getNode()));
    }

    private void updateLastUsed() {
        Set<BlockTransactionHashIndex> currentOutputs = currentEntry.getNode().getTransactionOutputs();
        if(AppServices.isConnected() && currentOutputs.isEmpty()) {
            lastUsed.setText("Never");
            lastUsed.setGraphic(getUnusedGlyph());
            address.getStyleClass().remove("error");
        } else if(!currentOutputs.isEmpty()) {
            long count = currentOutputs.size();
            BlockTransactionHashIndex lastUsedReference = currentOutputs.stream().skip(count - 1).findFirst().get();
            lastUsed.setText(lastUsedReference.getHeight() <= 0 ? "Unconfirmed Transaction" : (lastUsedReference.getDate() == null ? "Unknown" : DATE_FORMAT.format(lastUsedReference.getDate())));
            lastUsed.setGraphic(getWarningGlyph());
            if(!address.getStyleClass().contains("error")) {
                address.getStyleClass().add("error");
            }
        } else {
            lastUsed.setText("Unknown");
            lastUsed.setGraphic(getUnknownGlyph());
            address.getStyleClass().remove("error");
        }
    }

    private void updateDisplayAddress(List<Device> devices) {
        Wallet wallet = getWalletForm().getWallet();
        OutputDescriptor walletDescriptor = OutputDescriptor.getOutputDescriptor(walletForm.getWallet());
        List<String> walletFingerprints = walletDescriptor.getExtendedPublicKeys().stream().map(extKey -> walletDescriptor.getKeyDerivation(extKey).getMasterFingerprint()).collect(Collectors.toList());

        List<Device> addressDevices = devices.stream().filter(device -> walletFingerprints.contains(device.getFingerprint())).collect(Collectors.toList());
        if(addressDevices.isEmpty()) {
            addressDevices = devices.stream().filter(device -> device.isNeedsPinSent() || device.isNeedsPassphraseSent()).collect(Collectors.toList());
        }

        if(!addressDevices.isEmpty()) {
            if(currentEntry != null) {
                displayAddress.setVisible(true);
            }

            displayAddress.setUserData(addressDevices);
            return;
        } else if(currentEntry != null && wallet.getKeystores().stream().anyMatch(keystore -> keystore.getSource().equals(KeystoreSource.HW_USB))) {
            displayAddress.setVisible(true);
            displayAddress.setUserData(null);
            return;
        }

        displayAddress.setVisible(false);
        displayAddress.setUserData(null);
    }

    private Image getQrCode(String address) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix qrMatrix = qrCodeWriter.encode(address, BarcodeFormat.QR_CODE, 130, 130, Map.of(EncodeHintType.MARGIN, 2));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(qrMatrix, "PNG", baos, new MatrixToImageConfig());

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            return new Image(bais);
        } catch(Exception e) {
            log.error("Error generating QR", e);
        }

        return null;
    }

    public void getNewAddress(ActionEvent event) {
        refreshAddress();
    }

    public void refreshAddress() {
        NodeEntry freshEntry = getWalletForm().getFreshNodeEntry(KeyPurpose.RECEIVE, currentEntry);
        setNodeEntry(freshEntry);
    }

    @SuppressWarnings("unchecked")
    public void displayAddress(ActionEvent event) {
        Wallet wallet = getWalletForm().getWallet();
        if(currentEntry != null) {
            OutputDescriptor addressDescriptor = OutputDescriptor.getOutputDescriptor(walletForm.getWallet(), currentEntry.getNode().getKeyPurpose(), currentEntry.getNode().getIndex());

            List<Device> possibleDevices = (List<Device>)displayAddress.getUserData();
            if(possibleDevices != null && !possibleDevices.isEmpty()) {
                if(possibleDevices.size() > 1 || possibleDevices.get(0).isNeedsPinSent() || possibleDevices.get(0).isNeedsPassphraseSent()) {
                    DeviceAddressDialog dlg = new DeviceAddressDialog(wallet, addressDescriptor);
                    dlg.showAndWait();
                } else {
                    Device actualDevice = possibleDevices.get(0);
                    Hwi.DisplayAddressService displayAddressService = new Hwi.DisplayAddressService(actualDevice, "", wallet.getScriptType(), addressDescriptor);
                    displayAddressService.setOnFailed(failedEvent -> {
                        Platform.runLater(() -> {
                            DeviceAddressDialog dlg = new DeviceAddressDialog(wallet, addressDescriptor);
                            dlg.showAndWait();
                        });
                    });
                    displayAddressService.start();
                }
            } else {
                DeviceAddressDialog dlg = new DeviceAddressDialog(wallet, addressDescriptor);
                dlg.showAndWait();
            }
        }
    }

    public void clear() {
        if(currentEntry != null) {
            label.textProperty().unbindBidirectional(currentEntry.labelProperty());
        }

        address.setText("");
        label.setText("");
        derivationPath.setText("");
        lastUsed.setText("");
        lastUsed.setGraphic(null);
        qrCode.setImage(null);
        scriptPubKeyArea.clear();
        outputDescriptor.clear();
        this.currentEntry = null;
    }

    public static Glyph getUnusedGlyph() {
        Glyph checkGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.CHECK_CIRCLE);
        checkGlyph.getStyleClass().add("unused-check");
        checkGlyph.setFontSize(12);
        return checkGlyph;
    }

    public static Glyph getWarningGlyph() {
        Glyph duplicateGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.EXCLAMATION_CIRCLE);
        duplicateGlyph.getStyleClass().add("duplicate-warning");
        duplicateGlyph.setFontSize(12);
        return duplicateGlyph;
    }

    public static Glyph getUnknownGlyph() {
        Glyph duplicateGlyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.QUESTION_CIRCLE);
        duplicateGlyph.setFontSize(12);
        return duplicateGlyph;
    }

    @Subscribe
    public void walletAddressesChanged(WalletAddressesChangedEvent event) {
        displayAddress.setUserData(null);
    }

    @Subscribe
    public void receiveTo(ReceiveToEvent event) {
        if(event.getReceiveEntry().getWallet().equals(getWalletForm().getWallet())) {
            setNodeEntry(event.getReceiveEntry());
        }
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            if(currentEntry != null) {
                label.textProperty().unbindBidirectional(currentEntry.labelProperty());
                currentEntry = null;
            }
            refreshAddress();
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            if(currentEntry != null && event.getHistoryChangedNodes().contains(currentEntry.getNode())) {
                refreshAddress();
            }
        }
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        updateDisplayAddress(event.getDevices());
    }

    @Subscribe
    public void connection(ConnectionEvent event) {
        updateLastUsed();
    }

    @Subscribe
    public void disconnection(DisconnectionEvent event) {
        updateLastUsed();
    }
}