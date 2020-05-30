package com.sparrowwallet.sparrow.wallet;

import com.google.common.eventbus.Subscribe;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.CopyableLabel;
import com.sparrowwallet.sparrow.control.CopyableTextField;
import com.sparrowwallet.sparrow.event.ReceiveToEvent;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.fxmisc.richtext.CodeArea;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.ResourceBundle;

public class ReceiveController extends WalletFormController implements Initializable {
    @FXML
    private CopyableTextField address;

    @FXML
    private TextField label;

    @FXML
    private CopyableLabel derivationPath;

    @FXML
    private CopyableLabel lastUsed;

    @FXML
    private ImageView qrCode;

    @FXML
    private CodeArea scriptPubKeyArea;

    @FXML
    private CodeArea outputDescriptor;

    private NodeEntry currentEntry;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventManager.get().register(this);
    }

    @Override
    public void initializeView() {

    }

    public void setNodeEntry(NodeEntry nodeEntry) {
        if(currentEntry != null) {
            label.textProperty().unbindBidirectional(currentEntry.labelProperty());
        }

        this.currentEntry = nodeEntry;

        address.setText(nodeEntry.getAddress().toString());

        label.textProperty().bindBidirectional(nodeEntry.labelProperty());

        derivationPath.setText(nodeEntry.getNode().getDerivationPath());

        //TODO: Find last used block height if available (red flag?)
        lastUsed.setText("Unknown");

        Image qrImage = getQrCode(nodeEntry.getAddress().toString());
        if(qrImage != null) {
            qrCode.setImage(qrImage);
        }

        scriptPubKeyArea.clear();
        appendScript(scriptPubKeyArea, nodeEntry.getOutputScript(), null, null);

        outputDescriptor.clear();
        outputDescriptor.appendText(nodeEntry.getOutputDescriptor());
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
            e.printStackTrace();
        }

        return null;
    }

    public void getNewAddress(ActionEvent event) {
        NodeEntry freshEntry = getWalletForm().getFreshNodeEntry(KeyPurpose.RECEIVE, currentEntry);
        setNodeEntry(freshEntry);
    }

    @Subscribe
    public void receiveTo(ReceiveToEvent event) {
        setNodeEntry(event.getReceiveEntry());
    }
}
