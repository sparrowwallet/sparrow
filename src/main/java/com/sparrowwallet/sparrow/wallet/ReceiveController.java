package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.*;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.event.UsbDeviceEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.event.WalletNodesChangedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Device;
import com.sparrowwallet.sparrow.io.Hwi;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.glyphfont.Glyph;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
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

        updateDisplayAddress(AppController.getDevices());
    }

    private void updateDerivationPath(NodeEntry nodeEntry) {
        KeyDerivation firstDerivation = getWalletForm().getWallet().getKeystores().get(0).getKeyDerivation();
        boolean singleDerivationPath = true;
        for(Keystore keystore : getWalletForm().getWallet().getKeystores()) {
            if(!keystore.getKeyDerivation().getDerivationPath().equals(firstDerivation.getDerivationPath())) {
                singleDerivationPath = false;
                break;
            }
        }

        if(singleDerivationPath) {
            derivationPath.setText(firstDerivation.extend(nodeEntry.getNode().getDerivation()).getDerivationPath());
        } else {
            derivationPath.setText(nodeEntry.getNode().getDerivationPath().replace("m", "multi"));
        }
    }

    private void updateLastUsed() {
        Set<BlockTransactionHashIndex> currentOutputs = currentEntry.getNode().getTransactionOutputs();
        if(AppController.isOnline() && currentOutputs.isEmpty()) {
            lastUsed.setText("Never");
            lastUsed.setGraphic(getUnusedGlyph());
        } else if(!currentOutputs.isEmpty()) {
            long count = currentOutputs.size();
            BlockTransactionHashIndex lastUsedReference = currentOutputs.stream().skip(count - 1).findFirst().get();
            lastUsed.setText(lastUsedReference.getHeight() <= 0 ? "Unconfirmed Transaction" : DATE_FORMAT.format(lastUsedReference.getDate()));
            lastUsed.setGraphic(getWarningGlyph());
        } else {
            lastUsed.setText("Unknown");
            lastUsed.setGraphic(null);
        }
    }

    private void updateDisplayAddress(List<Device> devices) {
        //Can only display address for single sig wallets. See https://github.com/bitcoin-core/HWI/issues/224
        Wallet wallet = getWalletForm().getWallet();
        if(wallet.getPolicyType().equals(PolicyType.SINGLE)) {
            List<Device> addressDevices = devices.stream().filter(device -> wallet.getKeystores().get(0).getKeyDerivation().getMasterFingerprint().equals(device.getFingerprint())).collect(Collectors.toList());
            if(addressDevices.isEmpty()) {
                addressDevices = devices.stream().filter(device -> device.getNeedsPinSent() || device.getNeedsPassphraseSent()).collect(Collectors.toList());
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
        NodeEntry freshEntry = getWalletForm().getFreshNodeEntry(KeyPurpose.RECEIVE, currentEntry);
        setNodeEntry(freshEntry);
    }

    @SuppressWarnings("unchecked")
    public void displayAddress(ActionEvent event) {
        Wallet wallet = getWalletForm().getWallet();
        if(wallet.getPolicyType() == PolicyType.SINGLE && currentEntry != null) {
            Keystore keystore = wallet.getKeystores().get(0);
            KeyDerivation fullDerivation = keystore.getKeyDerivation().extend(currentEntry.getNode().getDerivation());

            List<Device> possibleDevices = (List<Device>)displayAddress.getUserData();
            if(possibleDevices != null && !possibleDevices.isEmpty()) {
                if(possibleDevices.size() > 1 || possibleDevices.get(0).getNeedsPinSent() || possibleDevices.get(0).getNeedsPassphraseSent()) {
                    DeviceAddressDialog dlg = new DeviceAddressDialog(List.of(keystore.getKeyDerivation().getMasterFingerprint()), wallet, fullDerivation);
                    dlg.showAndWait();
                } else {
                    Device actualDevice = possibleDevices.get(0);
                    Hwi.DisplayAddressService displayAddressService = new Hwi.DisplayAddressService(actualDevice, "", wallet.getScriptType(), fullDerivation.getDerivationPath());
                    displayAddressService.setOnFailed(failedEvent -> {
                        Platform.runLater(() -> {
                            DeviceAddressDialog dlg = new DeviceAddressDialog(List.of(keystore.getKeyDerivation().getMasterFingerprint()), wallet, fullDerivation);
                            dlg.showAndWait();
                        });
                    });
                    displayAddressService.start();
                }
            } else {
                DeviceAddressDialog dlg = new DeviceAddressDialog(List.of(keystore.getKeyDerivation().getMasterFingerprint()), wallet, fullDerivation);
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

    @Subscribe
    public void receiveTo(ReceiveToEvent event) {
        if(event.getReceiveEntry().getWallet().equals(getWalletForm().getWallet())) {
            setNodeEntry(event.getReceiveEntry());
        }
    }

    @Subscribe
    public void walletNodesChanged(WalletNodesChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            clear();
        }
    }

    @Subscribe
    public void walletHistoryChanged(WalletHistoryChangedEvent event) {
        if(event.getWallet().equals(walletForm.getWallet())) {
            if(currentEntry != null && event.getHistoryChangedNodes().contains(currentEntry.getNode())) {
                updateLastUsed();
            }
        }
    }

    @Subscribe
    public void usbDevicesFound(UsbDeviceEvent event) {
        updateDisplayAddress(event.getDevices());
    }
}