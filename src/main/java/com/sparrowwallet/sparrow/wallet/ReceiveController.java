package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.wallet.BlockTransactionHashIndex;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.control.CopyableTextField;
import com.sparrowwallet.sparrow.control.ScriptArea;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import com.sparrowwallet.sparrow.event.WalletHistoryChangedEvent;
import com.sparrowwallet.sparrow.event.WalletNodesChangedEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
import java.util.ResourceBundle;
import java.util.Set;

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

    private NodeEntry currentEntry;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {
        initializeScriptField(scriptPubKeyArea);
    }

    public void setNodeEntry(NodeEntry nodeEntry) {
        if(currentEntry != null) {
            label.textProperty().unbindBidirectional(currentEntry.labelProperty());
        }

        this.currentEntry = nodeEntry;
        address.setText(nodeEntry.getAddress().toString());
        label.textProperty().bindBidirectional(nodeEntry.labelProperty());
        derivationPath.setText(nodeEntry.getNode().getDerivationPath());

        updateLastUsed();

        Image qrImage = getQrCode(nodeEntry.getAddress().toString());
        if(qrImage != null) {
            qrCode.setImage(qrImage);
        }

        scriptPubKeyArea.clear();
        scriptPubKeyArea.appendScript(nodeEntry.getOutputScript(), null, null);

        outputDescriptor.clear();
        outputDescriptor.appendText(nodeEntry.getOutputDescriptor());
    }

    private void updateLastUsed() {
        Set<BlockTransactionHashIndex> currentOutputs = currentEntry.getNode().getTransactionOutputs();
        if(AppController.isOnline() && currentOutputs.isEmpty()) {
            lastUsed.setText("Never");
            lastUsed.setGraphic(getUnusedGlyph());
        } else if(!currentOutputs.isEmpty()) {
            long count = currentOutputs.size();
            BlockTransactionHashIndex lastUsedReference = currentOutputs.stream().skip(count - 1).findFirst().get();
            lastUsed.setText(DATE_FORMAT.format(lastUsedReference.getDate()));
            lastUsed.setGraphic(getWarningGlyph());
        } else {
            lastUsed.setText("Unknown");
            lastUsed.setGraphic(null);
        }
    }

    private Image getQrCode(String address) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix qrMatrix = qrCodeWriter.encode(address, BarcodeFormat.QR_CODE, 150, 150);

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
}
