package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.WebcamResolution;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.protocol.Base43;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.sparrow.AppController;
import com.sparrowwallet.hummingbird.ResultType;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.URDecoder;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import org.controlsfx.tools.Borders;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class QRScanDialog extends Dialog<QRScanDialog.Result> {
    private final URDecoder decoder;
    private final WebcamService webcamService;
    private List<String> parts;

    private boolean isUr;
    private QRScanDialog.Result result;

    private static final Pattern PART_PATTERN = Pattern.compile("p(\\d+)of(\\d+) (.+)");

    public QRScanDialog() {
        this.decoder = new URDecoder();

        this.webcamService = new WebcamService(WebcamResolution.VGA);
        WebcamView webcamView = new WebcamView(webcamService);

        final DialogPane dialogPane = getDialogPane();
        AppController.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(webcamView.getView());

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());

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
            Matcher partMatcher = PART_PATTERN.matcher(qrtext);

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
            } else if(partMatcher.matches()) {
                int m = Integer.parseInt(partMatcher.group(1));
                int n = Integer.parseInt(partMatcher.group(2));
                String payload = partMatcher.group(3);

                if(parts == null) {
                    parts = new ArrayList<>(n);
                    IntStream.range(0, n).forEach(i -> parts.add(null));
                }
                parts.set(m - 1, payload);

                if(parts.stream().filter(Objects::nonNull).count() == n) {
                    String complete = String.join("", parts);
                    try {
                        PSBT psbt = PSBT.fromString(complete);
                        result = new Result(psbt);
                        return;
                    } catch(Exception e) {
                        //ignore, bytes not parsable as PSBT
                    }

                    try {
                        Transaction transaction = new Transaction(Utils.hexToBytes(complete));
                        result = new Result(transaction);
                        return;
                    } catch(Exception e) {
                        //ignore, bytes not parsable as tx
                    }

                    result = new Result("Parsed QR parts were not a PSBT or transaction");
                }
            } else {
                PSBT psbt;
                Transaction transaction;
                BitcoinURI bitcoinURI;
                Address address;
                ExtendedKey extendedKey;
                try {
                    extendedKey = ExtendedKey.fromDescriptor(qrtext);
                    result = new Result(extendedKey);
                    return;
                } catch(Exception e) {
                    //Ignore, not a valid xpub
                }

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
                    psbt = new PSBT(Base43.decode(qrtext));
                    result = new Result(psbt);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as base43 decoded bytes
                }

                try {
                    transaction = new Transaction(Base43.decode(qrtext));
                    result = new Result(transaction);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as base43 decoded bytes
                }

                result = new Result(qrtext);
            }
        }
    }

    public static class Result {
        public final Transaction transaction;
        public final PSBT psbt;
        public final BitcoinURI uri;
        public final ExtendedKey extendedKey;
        public final String payload;
        public final Throwable exception;

        public Result(Transaction transaction) {
            this.transaction = transaction;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(PSBT psbt) {
            this.transaction = null;
            this.psbt = psbt;
            this.uri = null;
            this.extendedKey = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(BitcoinURI uri) {
            this.transaction = null;
            this.psbt = null;
            this.uri = uri;
            this.extendedKey = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(Address address) {
            this.transaction = null;
            this.psbt = null;
            this.uri = BitcoinURI.fromAddress(address);
            this.extendedKey = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(ExtendedKey extendedKey) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = extendedKey;
            this.payload = null;
            this.exception = null;
        }

        public Result(String payload) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.payload = payload;
            this.exception = null;
        }

        public Result(Throwable exception) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.payload = null;
            this.exception = exception;
        }
    }
}
