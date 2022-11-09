package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.input.KeyStroke;

import java.util.List;
import java.util.Map;

public class QRCodeDialog extends DialogWindow {
    public QRCodeDialog(String data) throws WriterException {
        super(data);

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel(new GridLayout(1).setLeftMarginSize(1).setRightMarginSize(1).setTopMarginSize(1));

        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix qrMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 33, 33, Map.of(EncodeHintType.MARGIN, 0));

        ImageComponent imageComponent = new ImageComponent();
        imageComponent.setTextImage(new QRTextImage(qrMatrix));
        mainPanel.addComponent(imageComponent);

        setComponent(mainPanel);
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        close();
        return true;
    }
}
