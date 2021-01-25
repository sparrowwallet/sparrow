package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamResolution;
import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.P2PKHAddress;
import com.sparrowwallet.drongo.address.P2SHAddress;
import com.sparrowwallet.drongo.address.P2WPKHAddress;
import com.sparrowwallet.drongo.crypto.*;
import com.sparrowwallet.drongo.protocol.Base43;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.protocol.Transaction;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.LegacyURDecoder;
import com.sparrowwallet.hummingbird.registry.*;
import com.sparrowwallet.hummingbird.ResultType;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.URDecoder;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.tools.Borders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@SuppressWarnings("deprecation")
public class QRScanDialog extends Dialog<QRScanDialog.Result> {
    private static final Logger log = LoggerFactory.getLogger(QRScanDialog.class);

    private final URDecoder decoder;
    private final LegacyURDecoder legacyDecoder;
    private final WebcamService webcamService;
    private List<String> parts;

    private boolean isUr;
    private QRScanDialog.Result result;

    private static final Pattern PART_PATTERN = Pattern.compile("p(\\d+)of(\\d+) (.+)");

    private final ObjectProperty<WebcamResolution> webcamResolutionProperty = new SimpleObjectProperty<>(WebcamResolution.VGA);

    public QRScanDialog() {
        this.decoder = new URDecoder();
        this.legacyDecoder = new LegacyURDecoder();

        if(Config.get().isHdCapture()) {
            webcamResolutionProperty.set(WebcamResolution.HD);
        }

        this.webcamService = new WebcamService(webcamResolutionProperty.get(), new QRScanListener());
        WebcamView webcamView = new WebcamView(webcamService);

        final DialogPane dialogPane = new QRScanDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(webcamView.getView());

        dialogPane.setContent(Borders.wrap(stackPane).lineBorder().buildAll());

        webcamService.resultProperty().addListener(new QRResultListener());
        webcamService.setOnFailed(failedEvent -> {
            Platform.runLater(() -> setResult(new Result(failedEvent.getSource().getException())));
        });
        webcamService.start();
        webcamResolutionProperty.addListener((observable, oldValue, newResolution) -> {
            if(newResolution != null) {
                setHeight(newResolution == WebcamResolution.HD ? (getHeight() - 100) : (getHeight() + 100));
            }
            webcamService.cancel();
        });

        setOnCloseRequest(event -> {
            boolean isHdCapture = (webcamResolutionProperty.get() == WebcamResolution.HD);
            if(Config.get().isHdCapture() != isHdCapture) {
                Config.get().setHdCapture(isHdCapture);
            }

            Platform.runLater(() -> webcamResolutionProperty.set(null));
        });

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType hdButtonType = new javafx.scene.control.ButtonType("Use HD Capture", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(hdButtonType, cancelButtonType);
        dialogPane.setPrefWidth(646);
        dialogPane.setPrefHeight(webcamResolutionProperty.get() == WebcamResolution.HD ? 450 : 550);

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

                if(LegacyURDecoder.isLegacyURFragment(qrtext)) {
                    legacyDecoder.receivePart(qrtext.toLowerCase());

                    if(legacyDecoder.isComplete()) {
                        try {
                            UR ur = legacyDecoder.decode();
                            result = extractResultFromUR(ur);
                        } catch(Exception e) {
                            result = new Result(new URException(e.getMessage()));
                        }
                    }
                } else {
                    decoder.receivePart(qrtext);

                    if(decoder.getResult() != null) {
                        URDecoder.Result urResult = decoder.getResult();
                        if(urResult.type == ResultType.SUCCESS) {
                            result = extractResultFromUR(urResult.ur);
                        } else {
                            result = new Result(new URException(urResult.error));
                        }
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

                    result = new Result(new ScanException("Parsed QR parts were not a PSBT or transaction"));
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

        private Result extractResultFromUR(UR ur) {
            try {
                RegistryType urRegistryType = ur.getRegistryType();

                if(urRegistryType.equals(RegistryType.BYTES)) {
                    byte[] urBytes = (byte[])ur.decodeFromRegistry();
                    try {
                        PSBT psbt = new PSBT(urBytes);
                        return new Result(psbt);
                    } catch(Exception e) {
                        //ignore, bytes not parsable as PSBT
                    }

                    try {
                        Transaction transaction = new Transaction(urBytes);
                        return new Result(transaction);
                    } catch(Exception e) {
                        //ignore, bytes not parsable as tx
                    }

                    try {
                        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder();
                        ByteBuffer buf = ByteBuffer.wrap(urBytes);
                        CharBuffer charBuffer = decoder.decode(buf);
                        return new Result(charBuffer.toString());
                    } catch(Exception e) {
                        //ignore, bytes not parsable as utf-8
                    }

                    result = new Result(new URException("Parsed UR of type " + urRegistryType + " was not a PSBT, transaction or UTF-8 text"));
                } else if(urRegistryType.equals(RegistryType.CRYPTO_PSBT)) {
                    CryptoPSBT cryptoPSBT = (CryptoPSBT)ur.decodeFromRegistry();
                    try {
                        PSBT psbt = new PSBT(cryptoPSBT.getPsbt());
                        return new Result(psbt);
                    } catch(Exception e) {
                        log.error("Error parsing PSBT from UR type " + urRegistryType, e);
                        return new Result(new URException("Error parsing PSBT from UR type " + urRegistryType, e));
                    }
                } else if(urRegistryType.equals(RegistryType.CRYPTO_ADDRESS)) {
                    CryptoAddress cryptoAddress = (CryptoAddress)ur.decodeFromRegistry();
                    Address address = getAddress(cryptoAddress);
                    if(address != null) {
                        return new Result(BitcoinURI.fromAddress(address));
                    } else {
                        return new Result(new URException("Unknown " + urRegistryType + " type of " + cryptoAddress.getType()));
                    }
                } else if(urRegistryType.equals(RegistryType.CRYPTO_HDKEY)) {
                    CryptoHDKey cryptoHDKey = (CryptoHDKey)ur.decodeFromRegistry();
                    ExtendedKey extendedKey = getExtendedKey(cryptoHDKey);
                    return new Result(extendedKey);
                } else if(urRegistryType.equals(RegistryType.CRYPTO_OUTPUT)) {
                    CryptoOutput cryptoOutput = (CryptoOutput)ur.decodeFromRegistry();
                    OutputDescriptor outputDescriptor = getOutputDescriptor(cryptoOutput);
                    return new Result(outputDescriptor);
                } else if(urRegistryType.equals(RegistryType.CRYPTO_ACCOUNT)) {
                    CryptoAccount cryptoAccount = (CryptoAccount)ur.decodeFromRegistry();
                    List<Wallet> wallets = getWallets(cryptoAccount);
                    return new Result(wallets);
                } else {
                    log.error("Unsupported UR type " + urRegistryType);
                    return new Result(new URException("UR type " + urRegistryType + " is not supported"));
                }
            } catch(IllegalArgumentException e) {
                log.error("Unknown UR type of " + ur.getType(), e);
                return new Result(new URException("Unknown UR type of " + ur.getType(), e));
            } catch(UR.InvalidCBORException e) {
                log.error("Invalid CBOR in UR", e);
                return new Result(new URException("Invalid CBOR in UR", e));
            } catch(Exception e) {
                log.error("Error parsing UR CBOR", e);
                return new Result(new URException("Error parsing UR CBOR", e));
            }

            return null;
        }

        private Address getAddress(CryptoAddress cryptoAddress) {
            Address address = null;
            if(cryptoAddress.getType() == null || cryptoAddress.getType() == CryptoAddress.Type.P2PKH) {
                address = new P2PKHAddress(cryptoAddress.getData());
            } else if(cryptoAddress.getType() == CryptoAddress.Type.P2SH) {
                address = new P2SHAddress(cryptoAddress.getData());
            } else if(cryptoAddress.getType() == CryptoAddress.Type.P2WPKH) {
                address = new P2WPKHAddress(cryptoAddress.getData());
            }
            return address;
        }

        private ExtendedKey getExtendedKey(CryptoHDKey cryptoHDKey) {
            if(cryptoHDKey.isPrivateKey()) {
                DeterministicKey prvKey = HDKeyDerivation.createMasterPrivKeyFromBytes(Arrays.copyOfRange(cryptoHDKey.getKey(), 1, 33), cryptoHDKey.getChainCode(), List.of(ChildNumber.ZERO));
                return new ExtendedKey(prvKey, new byte[4], ChildNumber.ZERO);
            } else {
                ChildNumber lastChild = ChildNumber.ZERO;
                int depth = 1;
                byte[] parentFingerprint = new byte[4];
                if(cryptoHDKey.getOrigin() != null) {
                    if(!cryptoHDKey.getOrigin().getComponents().isEmpty()) {
                        PathComponent lastComponent = cryptoHDKey.getOrigin().getComponents().get(cryptoHDKey.getOrigin().getComponents().size() - 1);
                        lastChild = new ChildNumber(lastComponent.getIndex(), lastComponent.isHardened());
                        depth = cryptoHDKey.getOrigin().getComponents().size();
                    }
                    if(cryptoHDKey.getParentFingerprint() != null) {
                        parentFingerprint = cryptoHDKey.getParentFingerprint();
                    }
                }
                DeterministicKey pubKey = new DeterministicKey(List.of(lastChild), cryptoHDKey.getChainCode(), cryptoHDKey.getKey(), depth, parentFingerprint);
                return new ExtendedKey(pubKey, parentFingerprint, lastChild);
            }
        }

        private OutputDescriptor getOutputDescriptor(CryptoOutput cryptoOutput) {
            ScriptType scriptType = getScriptType(cryptoOutput.getScriptExpressions());

            if(cryptoOutput.getMultiKey() != null) {
                MultiKey multiKey = cryptoOutput.getMultiKey();
                Map<ExtendedKey, KeyDerivation> extendedPublicKeys = new LinkedHashMap<>();
                for(CryptoHDKey cryptoHDKey : multiKey.getHdKeys()) {
                    ExtendedKey extendedKey = getExtendedKey(cryptoHDKey);
                    KeyDerivation keyDerivation = getKeyDerivation(cryptoHDKey.getOrigin());
                    extendedPublicKeys.put(extendedKey, keyDerivation);
                }
                return new OutputDescriptor(scriptType, multiKey.getThreshold(), extendedPublicKeys);
            } else if(cryptoOutput.getEcKey() != null) {
                throw new IllegalArgumentException("EC keys are currently unsupported");
            } else if(cryptoOutput.getHdKey() != null) {
                ExtendedKey extendedKey = getExtendedKey(cryptoOutput.getHdKey());
                KeyDerivation keyDerivation = getKeyDerivation(cryptoOutput.getHdKey().getOrigin());
                return new OutputDescriptor(scriptType, extendedKey, keyDerivation);
            }

            throw new IllegalStateException("CryptoOutput did not contain sufficient information");
        }

        private List<Wallet> getWallets(CryptoAccount cryptoAccount) {
            List<Wallet> wallets = new ArrayList<>();
            String masterFingerprint = Utils.bytesToHex(cryptoAccount.getMasterFingerprint());
            for(CryptoOutput cryptoOutput : cryptoAccount.getOutputDescriptors()) {
                Wallet wallet = new Wallet();
                OutputDescriptor outputDescriptor = getOutputDescriptor(cryptoOutput);
                if(outputDescriptor.isMultisig()) {
                    throw new IllegalStateException("Multisig output descriptors are unsupported in CryptoAccount");
                }

                ExtendedKey extendedKey = outputDescriptor.getSingletonExtendedPublicKey();
                wallet.setScriptType(outputDescriptor.getScriptType());
                Keystore keystore = new Keystore();
                keystore.setKeyDerivation(new KeyDerivation(masterFingerprint, outputDescriptor.getKeyDerivation(extendedKey).getDerivationPath()));
                keystore.setExtendedPublicKey(extendedKey);
                wallet.getKeystores().add(keystore);
                wallets.add(wallet);
            }

            return wallets;
        }

        private ScriptType getScriptType(List<ScriptExpression> expressions) {
            if(List.of(ScriptExpression.PUBLIC_KEY_HASH).equals(expressions)) {
                return ScriptType.P2PKH;
            } else if(List.of(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_PUBLIC_KEY_HASH).equals(expressions)) {
                return ScriptType.P2SH_P2WPKH;
            } else if(List.of(ScriptExpression.WITNESS_PUBLIC_KEY_HASH).equals(expressions)) {
                return ScriptType.P2WPKH;
            } else if(List.of(ScriptExpression.SCRIPT_HASH).equals(expressions)) {
                return ScriptType.P2SH;
            } else if(List.of(ScriptExpression.SCRIPT_HASH, ScriptExpression.WITNESS_SCRIPT_HASH).equals(expressions)) {
                return ScriptType.P2SH_P2WSH;
            } else if(List.of(ScriptExpression.WITNESS_SCRIPT_HASH).equals(expressions)) {
                return ScriptType.P2WSH;
            }

            throw new IllegalArgumentException("Unknown script of " + expressions);
        }

        private KeyDerivation getKeyDerivation(CryptoKeypath cryptoKeypath) {
            if(cryptoKeypath != null) {
                return new KeyDerivation(Utils.bytesToHex(cryptoKeypath.getSourceFingerprint()), cryptoKeypath.getPath());
            }

            return null;
        }
    }

    private class QRScanListener implements WebcamListener {
        @Override
        public void webcamOpen(WebcamEvent webcamEvent) {

        }

        @Override
        public void webcamClosed(WebcamEvent webcamEvent) {
            if(webcamResolutionProperty.get() != null) {
                webcamService.setResolution(webcamResolutionProperty.get());
                Platform.runLater(() -> {
                    if(!webcamService.isRunning()) {
                        webcamService.reset();
                        webcamService.start();
                    }
                });
            }
        }

        @Override
        public void webcamDisposed(WebcamEvent webcamEvent) {

        }

        @Override
        public void webcamImageObtained(WebcamEvent webcamEvent) {

        }
    }

    private class QRScanDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                ToggleButton hd = new ToggleButton(buttonType.getText());
                hd.setSelected(webcamResolutionProperty.get() == WebcamResolution.HD);
                hd.setGraphicTextGap(5);
                setHdGraphic(hd, hd.isSelected());

                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(hd, buttonData);
                hd.selectedProperty().addListener((observable, oldValue, newValue) -> {
                    webcamResolutionProperty.set(newValue ? WebcamResolution.HD : WebcamResolution.VGA);
                    setHdGraphic(hd, newValue);
                });

                return hd;
            }

            return super.createButton(buttonType);
        }

        private void setHdGraphic(ToggleButton hd, boolean isHd) {
            if(isHd) {
                hd.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE));
            } else {
                hd.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE));
            }
        }

        private Glyph getGlyph(FontAwesome5.Glyph glyphName) {
            Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
            glyph.setFontSize(11);
            return glyph;
        }
    }

    public static class Result {
        public final Transaction transaction;
        public final PSBT psbt;
        public final BitcoinURI uri;
        public final ExtendedKey extendedKey;
        public final OutputDescriptor outputDescriptor;
        public final List<Wallet> wallets;
        public final String payload;
        public final Throwable exception;

        public Result(Transaction transaction) {
            this.transaction = transaction;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(PSBT psbt) {
            this.transaction = null;
            this.psbt = psbt;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(BitcoinURI uri) {
            this.transaction = null;
            this.psbt = null;
            this.uri = uri;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(Address address) {
            this.transaction = null;
            this.psbt = null;
            this.uri = BitcoinURI.fromAddress(address);
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(ExtendedKey extendedKey) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = extendedKey;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(OutputDescriptor outputDescriptor) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = outputDescriptor;
            this.wallets = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(List<Wallet> wallets) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = wallets;
            this.payload = null;
            this.exception = null;
        }

        public Result(String payload) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = payload;
            this.exception = null;
        }

        public Result(Throwable exception) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.payload = null;
            this.exception = exception;
        }
    }

    public static class ScanException extends Exception {
        public ScanException() {
            super();
        }

        public ScanException(String message) {
            super(message);
        }

        public ScanException(Throwable cause) {
            super(cause);
        }

        public ScanException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class URException extends ScanException {
        public URException() {
            super();
        }

        public URException(String message) {
            super(message);
        }

        public URException(Throwable cause) {
            super(cause);
        }

        public URException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
