package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.WebcamResolution;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Base43;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.sparrow.ur.ResultType;
import com.sparrowwallet.sparrow.ur.UR;
import com.sparrowwallet.sparrow.ur.URDecoder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import org.controlsfx.tools.Borders;

public class QRScanDialog extends Dialog<QRScanDialog.Result> {
    private final URDecoder decoder;
    private final WebcamService webcamService;

    private boolean isUr;
    private QRScanDialog.Result result;

    public QRScanDialog() {
        this.decoder = new URDecoder();

        this.webcamService = new WebcamService(WebcamResolution.VGA);
        WebcamView webcamView = new WebcamView(webcamService);

        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(webcamView.getView());

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().outerPadding(0).innerPadding(0).buildAll());

        webcamService.resultProperty().addListener(new QRResultListener());
        webcamService.setOnFailed(failedEvent -> {
            Platform.runLater(() -> setResult(new Result(failedEvent.getSource().getException())));
        });
        webcamService.start();
        setOnCloseRequest(event -> {
            Platform.runLater(webcamService::cancel);
        });

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialogPane.getButtonTypes().addAll(cancelButtonType);
        dialogPane.setPrefWidth(660);
        dialogPane.setPrefHeight(550);

        setResultConverter(dialogButton -> dialogButton != cancelButtonType ? result : null);
    }

    private class QRResultListener implements ChangeListener<com.google.zxing.Result> {
        @Override
        public void changed(ObservableValue<? extends com.google.zxing.Result> observable, com.google.zxing.Result oldValue, com.google.zxing.Result qrResult) {
            if(result != null) {
                Platform.runLater(() -> setResult(result));
            }

            //Try text first
            String qrtext = qrResult.getText();
            if(isUr || qrtext.toLowerCase().startsWith(UR.UR_PREFIX)) {
                isUr = true;
                decoder.receivePart(qrtext);

                if(decoder.getResult() != null) {
                    URDecoder.Result urResult = decoder.getResult();
                    if(urResult.type == ResultType.SUCCESS) {
                        //TODO: Confirm once UR type registry is updated
                        if(urResult.ur.getType().contains(UR.BYTES_TYPE) || urResult.ur.getType().equals(UR.CRYPTO_PSBT_TYPE)) {
                            try {
                                PSBT psbt = new PSBT(urResult.ur.toBytes());
                                result = new Result(psbt);
                                return;
                            } catch(Exception e) {
                                //ignore, bytes not parsable as PSBT
                            }

                            try {
                                Transaction transaction = new Transaction(urResult.ur.toBytes());
                                result = new Result(transaction);
                                return;
                            } catch(Exception e) {
                                //ignore, bytes not parsable as tx
                            }

                            result = new Result("Parsed UR of type " + urResult.ur.getType() + " was not a PSBT or transaction");
                        } else {
                            result = new Result("Cannot parse UR type of " + urResult.ur.getType());
                        }
                    } else {
                        result = new Result(urResult.error);
                    }
                }
            } else {
                PSBT psbt;
                Transaction transaction;
                BitcoinURI bitcoinURI;
                Address address;
                try {
                    bitcoinURI = new BitcoinURI(qrtext);
                    result = new Result(bitcoinURI);
                    return;
                } catch(Exception e) {
                    //Ignore, not an BIP 21 URI
                }

                try {
                    address = Address.fromString(qrtext);
                    result = new Result(address);
                    return;
                } catch(Exception e) {
                    //Ignore, not an address
                }

                try {
                    psbt = PSBT.fromString(qrtext);
                    result = new Result(psbt);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as Base64 or hex
                }

                try {
                    psbt = new PSBT(qrResult.getRawBytes());
                    result = new Result(psbt);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as raw bytes
                }

                try {
                    transaction = new Transaction(Utils.hexToBytes(qrtext));
                    result = new Result(transaction);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as hex
                }

                try {
                    transaction = new Transaction(qrResult.getRawBytes());
                    result = new Result(transaction);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as raw bytes
                }

                //Try Base43 used by Electrum
                try {
                    psbt = new PSBT(Base43.decode(qrResult.getText()));
                    result = new Result(psbt);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as base43 decoded bytes
                }

                try {
                    transaction = new Transaction(Base43.decode(qrResult.getText()));
                    result = new Result(transaction);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as base43 decoded bytes
                }

                result = new Result("Cannot parse QR code into a PSBT, transaction or address");
            }
        }
    }

    public static class Result {
        public final Transaction transaction;
        public final PSBT psbt;
        public final BitcoinURI uri;
        public final String error;
        public final Throwable exception;

        public Result(Transaction transaction) {
            this.transaction = transaction;
            this.psbt = null;
            this.uri = null;
            this.error = null;
            this.exception = null;
        }

        public Result(PSBT psbt) {
            this.transaction = null;
            this.psbt = psbt;
            this.uri = null;
            this.error = null;
            this.exception = null;
        }

        public Result(BitcoinURI uri) {
            this.transaction = null;
            this.psbt = null;
            this.uri = uri;
            this.error = null;
            this.exception = null;
        }

        public Result(Address address) {
            this.transaction = null;
            this.psbt = null;
            this.uri = BitcoinURI.fromAddress(address);
            this.error = null;
            this.exception = null;
        }

        public Result(String error) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.error = error;
            this.exception = null;
        }

        public Result(Throwable exception) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.error = null;
            this.exception = exception;
        }
    }
}
