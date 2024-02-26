package com.sparrowwallet.sparrow.control;

import com.github.sarxos.webcam.*;
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
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.uri.BitcoinURI;
import com.sparrowwallet.drongo.wallet.Bip39MnemonicCode;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.SeedQR;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.hummingbird.LegacyURDecoder;
import com.sparrowwallet.hummingbird.registry.*;
import com.sparrowwallet.hummingbird.ResultType;
import com.sparrowwallet.hummingbird.UR;
import com.sparrowwallet.hummingbird.URDecoder;
import com.sparrowwallet.hummingbird.registry.pathcomponent.IndexPathComponent;
import com.sparrowwallet.hummingbird.registry.pathcomponent.PathComponent;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.bbqr.BBQRDecoder;
import com.sparrowwallet.sparrow.io.bbqr.BBQRException;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import javafx.util.StringConverter;
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
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings("deprecation")
public class QRScanDialog extends Dialog<QRScanDialog.Result> {
    private static final Logger log = LoggerFactory.getLogger(QRScanDialog.class);

    private final URDecoder urDecoder;
    private final LegacyURDecoder legacyUrDecoder;
    private final BBQRDecoder bbqrDecoder;
    private final WebcamService webcamService;
    private List<String> parts;

    private QRScanDialog.Result result;

    private static final Pattern PART_PATTERN = Pattern.compile("p(\\d+)of(\\d+) (.+)");

    private static final int SCAN_PERIOD_MILLIS = 100;
    private final ObjectProperty<WebcamResolution> webcamResolutionProperty = new SimpleObjectProperty<>(WebcamResolution.VGA);

    private final DoubleProperty percentComplete = new SimpleDoubleProperty(0.0);

    private final ObjectProperty<WebcamDevice> webcamDeviceProperty = new SimpleObjectProperty<>();

    public QRScanDialog() {
        this.urDecoder = new URDecoder();
        this.legacyUrDecoder = new LegacyURDecoder();
        this.bbqrDecoder = new BBQRDecoder();

        if(Config.get().isHdCapture()) {
            webcamResolutionProperty.set(WebcamResolution.HD);
        }

        this.webcamService = new WebcamService(webcamResolutionProperty.get(), null, new QRScanListener(), new ScanDelayCalculator());
        webcamService.setPeriod(Duration.millis(SCAN_PERIOD_MILLIS));
        webcamService.setRestartOnFailure(false);
        WebcamView webcamView = new WebcamView(webcamService);

        final DialogPane dialogPane = new QRScanDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(webcamView.getView());
        Node wrappedView = Borders.wrap(stackPane).lineBorder().buildAll();

        ProgressBar progressBar = new ProgressBar();
        progressBar.setMinHeight(20);
        progressBar.setPadding(new Insets(0, 10, 0, 10));
        progressBar.setPrefWidth(Integer.MAX_VALUE);
        progressBar.progressProperty().bind(percentComplete);
        webcamService.openingProperty().addListener((observable, oldValue, newValue) -> {
            if(percentComplete.get() <= 0.0) {
                Platform.runLater(() -> percentComplete.set(newValue ? 0.0 : -1.0));
            }
            Platform.runLater(() -> {
                if(Config.get().getWebcamDevice() != null && webcamDeviceProperty.get() == null) {
                    for(WebcamDevice device : WebcamScanDriver.getFoundDevices()) {
                        if(device.getName().equals(Config.get().getWebcamDevice())) {
                            webcamDeviceProperty.set(device);
                        }
                    }
                }
            });
        });

        VBox vBox = new VBox(20);
        vBox.getChildren().addAll(wrappedView, progressBar);

        dialogPane.setContent(vBox);

        webcamService.resultProperty().addListener(new QRResultListener());
        webcamService.setOnFailed(failedEvent -> {
            Throwable exception = failedEvent.getSource().getException();

            Throwable nested = exception;
            while(nested.getCause() != null) {
                nested = nested.getCause();
            }
            if(org.controlsfx.tools.Platform.getCurrent() == org.controlsfx.tools.Platform.WINDOWS &&
                    nested.getMessage().startsWith("Library 'OpenIMAJGrabber' was not loaded successfully from file")) {
                exception = new WebcamDependencyException("Your system is missing a dependency required for the webcam. Follow the link below for more details.\n\n[https://sparrowwallet.com/docs/faq.html#your-system-is-missing-a-dependency-for-the-webcam]", exception);
            } else if(nested.getMessage().startsWith("Cannot start native grabber") && Config.get().getWebcamDevice() != null) {
                exception = new WebcamOpenException("Cannot open configured webcam " + Config.get().getWebcamDevice() + ", reverting to the default webcam");
                Config.get().setWebcamDevice(null);
            }

            final Throwable result = exception;
            Platform.runLater(() -> setResult(new Result(result)));
        });
        webcamService.start();
        webcamResolutionProperty.addListener((observable, oldValue, newResolution) -> {
            if(newResolution != null) {
                setHeight(newResolution == WebcamResolution.HD ? (getHeight() - 100) : (getHeight() + 100));
            }
            webcamService.cancel();
        });
        webcamDeviceProperty.addListener((observable, oldValue, newValue) -> {
            Config.get().setWebcamDevice(newValue.getName());
            if(!Objects.equals(webcamService.getDevice(), newValue)) {
                webcamService.cancel();
            }
        });

        setOnCloseRequest(event -> {
            boolean isHdCapture = (webcamResolutionProperty.get() == WebcamResolution.HD);
            if(Config.get().isHdCapture() != isHdCapture) {
                Config.get().setHdCapture(isHdCapture);
            }

            Platform.runLater(() -> webcamResolutionProperty.set(null));
        });

        final ButtonType cancelButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        final ButtonType hdButtonType = new javafx.scene.control.ButtonType("Use HD Capture", ButtonBar.ButtonData.LEFT);
        final ButtonType camButtonType = new javafx.scene.control.ButtonType("Default Camera", ButtonBar.ButtonData.HELP_2);
        dialogPane.getButtonTypes().addAll(hdButtonType, camButtonType, cancelButtonType);
        dialogPane.setPrefWidth(646);
        dialogPane.setPrefHeight(webcamResolutionProperty.get() == WebcamResolution.HD ? 490 : 590);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.moveToActiveWindowScreen(this);

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

            if(qrtext.toLowerCase(Locale.ROOT).startsWith(UR.UR_PREFIX)) {
                if(LegacyURDecoder.isLegacyURFragment(qrtext)) {
                    legacyUrDecoder.receivePart(qrtext);
                    Platform.runLater(() -> percentComplete.setValue(legacyUrDecoder.getPercentComplete()));

                    if(legacyUrDecoder.isComplete()) {
                        try {
                            UR ur = legacyUrDecoder.decode();
                            result = extractResultFromUR(ur);
                        } catch(Exception e) {
                            result = new Result(new URException(e.getMessage()));
                        }
                    }
                } else {
                    urDecoder.receivePart(qrtext);
                    Platform.runLater(() -> percentComplete.setValue(urDecoder.getProcessedPartsCount() > 0 ? urDecoder.getEstimatedPercentComplete() : 0));

                    if(urDecoder.getResult() != null) {
                        URDecoder.Result urResult = urDecoder.getResult();
                        if(urResult.type == ResultType.SUCCESS) {
                            result = extractResultFromUR(urResult.ur);
                            Platform.runLater(() -> setResult(result));
                        } else {
                            result = new Result(new URException(urResult.error));
                        }
                    }
                }
            } else if(BBQRDecoder.isBBQRFragment(qrtext)) {
                bbqrDecoder.receivePart(qrtext);
                Platform.runLater(() -> percentComplete.setValue(bbqrDecoder.getPercentComplete()));

                if(bbqrDecoder.getResult() != null) {
                    BBQRDecoder.Result bbqrResult = bbqrDecoder.getResult();
                    if(bbqrResult.getResultType() == BBQRDecoder.ResultType.SUCCESS) {
                        result = extractResultFromBBQR(bbqrResult);
                        Platform.runLater(() -> setResult(result));
                    } else {
                        result = new Result(new BBQRException(bbqrResult.getError()));
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

                if(n > 0) {
                    Platform.runLater(() -> percentComplete.setValue((double)parts.stream().filter(Objects::nonNull).count() / n));
                }

                if(parts.stream().filter(Objects::nonNull).count() == n) {
                    String complete = String.join("", parts);
                    try {
                        PSBT psbt = PSBT.fromString(complete, false);
                        result = new Result(psbt);
                        return;
                    } catch(PSBTParseException e) {
                        if(PSBT.isPSBT(complete)) {
                            log.error("Error parsing PSBT", e);
                        }
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

                    result = new Result(complete);
                }
            } else {
                PSBT psbt;
                Transaction transaction;
                BitcoinURI bitcoinURI;
                Address address;
                ExtendedKey extendedKey;
                DeterministicSeed seed;
                try {
                    extendedKey = ExtendedKey.fromDescriptor(qrtext);
                    result = new Result(extendedKey, null);
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
                    psbt = PSBT.fromString(qrtext, false);
                    result = new Result(psbt);
                    return;
                } catch(PSBTParseException e) {
                    if(PSBT.isPSBT(qrtext)) {
                        log.error("Error parsing PSBT", e);
                    }
                } catch(Exception e) {
                    //Ignore, not parseable as Base64 or hex
                }

                try {
                    psbt = new PSBT(qrResult.getRawBytes(), false);
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
                    psbt = new PSBT(Base43.decode(qrtext), false);
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

                try {
                    seed = SeedQR.getSeed(qrtext);
                    result = new Result(seed);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as a SeedQR
                }

                try {
                    seed = SeedQR.getSeed(qrResult.getRawBytes());
                    result = new Result(seed);
                    return;
                } catch(Exception e) {
                    //Ignore, not parseable as a CompactSeedQR
                }

                try {
                    List<String> words = Arrays.asList(qrtext.split(" "));
                    if(words.size() == 12 || words.size() == 15 || words.size() == 18 || words.size() == 21 || words.size() == 24) {
                        Bip39MnemonicCode.INSTANCE.check(words);
                        result = new Result(new DeterministicSeed(words, null, System.currentTimeMillis(), DeterministicSeed.Type.BIP39));
                        return;
                    }
                } catch(Exception e) {
                    //Ignore, not parseable as BIP39 seed words
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
                        PSBT psbt = new PSBT(urBytes, false);
                        return new Result(psbt);
                    } catch(PSBTParseException e) {
                        if(PSBT.isPSBT(urBytes)) {
                            log.error("Error parsing PSBT", e);
                        }
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
                        PSBT psbt = new PSBT(cryptoPSBT.getPsbt(), false);
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
                    return new Result(extendedKey, cryptoHDKey.getName());
                } else if(urRegistryType.equals(RegistryType.CRYPTO_OUTPUT)) {
                    CryptoOutput cryptoOutput = (CryptoOutput)ur.decodeFromRegistry();
                    OutputDescriptor outputDescriptor = getOutputDescriptor(cryptoOutput);
                    return new Result(outputDescriptor);
                } else if(urRegistryType.equals(RegistryType.CRYPTO_ACCOUNT)) {
                    CryptoAccount cryptoAccount = (CryptoAccount)ur.decodeFromRegistry();
                    List<Wallet> wallets = getWallets(cryptoAccount);
                    return new Result(wallets);
                } else if(urRegistryType.equals(RegistryType.CRYPTO_SEED)) {
                    CryptoSeed cryptoSeed = (CryptoSeed)ur.decodeFromRegistry();
                    DeterministicSeed seed = getSeed(cryptoSeed);
                    return new Result(seed);
                } else if(urRegistryType.equals(RegistryType.CRYPTO_BIP39)) {
                    CryptoBip39 cryptoBip39 = (CryptoBip39)ur.decodeFromRegistry();
                    DeterministicSeed seed = getSeed(cryptoBip39);
                    return new Result(seed);
                } else if(urRegistryType.equals(RegistryType.PSBT)) {
                    URPSBT urPSBT = (URPSBT)ur.decodeFromRegistry();
                    try {
                        PSBT psbt = new PSBT(urPSBT.getPsbt(), false);
                        return new Result(psbt);
                    } catch(Exception e) {
                        log.error("Error parsing PSBT from UR type " + urRegistryType, e);
                        return new Result(new URException("Error parsing PSBT from UR type " + urRegistryType, e));
                    }
                } else if(urRegistryType.equals(RegistryType.ADDRESS)) {
                    URAddress urAddress = (URAddress)ur.decodeFromRegistry();
                    Address address = getAddress(urAddress);
                    if(address != null) {
                        return new Result(BitcoinURI.fromAddress(address));
                    } else {
                        return new Result(new URException("Unknown " + urRegistryType + " type of " + urAddress.getType()));
                    }
                } else if(urRegistryType.equals(RegistryType.HDKEY)) {
                    URHDKey urHDKey = (URHDKey)ur.decodeFromRegistry();
                    ExtendedKey extendedKey = getExtendedKey(urHDKey);
                    return new Result(extendedKey, urHDKey.getName());
                } else if(urRegistryType.equals(RegistryType.OUTPUT_DESCRIPTOR)) {
                    UROutputDescriptor urOutputDescriptor = (UROutputDescriptor)ur.decodeFromRegistry();
                    OutputDescriptor outputDescriptor = getOutputDescriptor(urOutputDescriptor);
                    return new Result(outputDescriptor);
                } else if(urRegistryType.equals(RegistryType.ACCOUNT_DESCRIPTOR)) {
                    URAccountDescriptor urAccountDescriptor = (URAccountDescriptor)ur.decodeFromRegistry();
                    List<Wallet> wallets = getWallets(urAccountDescriptor);
                    return new Result(wallets);
                } else if(urRegistryType.equals(RegistryType.SEED)) {
                    URSeed urSeed = (URSeed)ur.decodeFromRegistry();
                    DeterministicSeed seed = getSeed(urSeed);
                    return new Result(seed);
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
                        if(lastComponent instanceof IndexPathComponent indexPathComponent) {
                            lastChild = new ChildNumber(indexPathComponent.getIndex(), indexPathComponent.isHardened());
                        }
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
                Map<ExtendedKey, String> extendedPublicKeyLabels = new LinkedHashMap<>();
                for(CryptoHDKey cryptoHDKey : multiKey.getHdKeys()) {
                    ExtendedKey extendedKey = getExtendedKey(cryptoHDKey);
                    KeyDerivation keyDerivation = getKeyDerivation(cryptoHDKey.getOrigin());
                    extendedPublicKeys.put(extendedKey, keyDerivation);
                    if(cryptoHDKey.getName() != null) {
                        extendedPublicKeyLabels.put(extendedKey, cryptoHDKey.getName());
                    }
                }
                return new OutputDescriptor(scriptType, multiKey.getThreshold(), extendedPublicKeys, new LinkedHashMap<>(), extendedPublicKeyLabels);
            } else if(cryptoOutput.getEcKey() != null) {
                throw new IllegalArgumentException("EC keys are currently unsupported");
            } else if(cryptoOutput.getHdKey() != null) {
                ExtendedKey extendedKey = getExtendedKey(cryptoOutput.getHdKey());
                KeyDerivation keyDerivation = getKeyDerivation(cryptoOutput.getHdKey().getOrigin());
                return new OutputDescriptor(scriptType, extendedKey, keyDerivation, cryptoOutput.getHdKey().getName());
            }

            throw new IllegalStateException("CryptoOutput did not contain sufficient information");
        }

        private List<Wallet> getWallets(CryptoAccount cryptoAccount) {
            List<Wallet> wallets = new ArrayList<>();
            String masterFingerprint = Utils.bytesToHex(cryptoAccount.getMasterFingerprint());
            for(CryptoOutput cryptoOutput : cryptoAccount.getOutputDescriptors()) {
                OutputDescriptor outputDescriptor = getOutputDescriptor(cryptoOutput);
                Wallet wallet = outputDescriptor.toKeystoreWallet(masterFingerprint);
                wallets.add(wallet);
            }

            return wallets;
        }

        private ScriptType getScriptType(List<ScriptExpression> scriptExpressions) {
            List<ScriptExpression> expressions = new ArrayList<>(scriptExpressions);
            if(expressions.get(expressions.size() - 1) == ScriptExpression.MULTISIG
                    || expressions.get(expressions.size() - 1) == ScriptExpression.SORTED_MULTISIG
                    || expressions.get(expressions.size() - 1) == ScriptExpression.COSIGNER) {
                expressions.remove(expressions.size() - 1);
            }

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
            } else if(List.of(ScriptExpression.TAPROOT).equals(expressions)) {
                return ScriptType.P2TR;
            }

            throw new IllegalArgumentException("Unknown script of " + expressions);
        }

        private KeyDerivation getKeyDerivation(CryptoKeypath cryptoKeypath) {
            if(cryptoKeypath != null) {
                if(!cryptoKeypath.getComponents().stream().allMatch(pathComponent -> pathComponent instanceof IndexPathComponent)) {
                    throw new IllegalArgumentException("Only indexed derivation path components are supported");
                }

                List<ChildNumber> path = cryptoKeypath.getComponents().stream().map(comp -> (IndexPathComponent)comp)
                        .map(comp -> new ChildNumber(comp.getIndex(), comp.isHardened())).collect(Collectors.toList());
                return new KeyDerivation(Utils.bytesToHex(cryptoKeypath.getSourceFingerprint()), KeyDerivation.writePath(path));
            }

            return null;
        }

        private DeterministicSeed getSeed(CryptoSeed cryptoSeed) {
            return new DeterministicSeed(cryptoSeed.getSeed(), null, cryptoSeed.getBirthdate() == null ? System.currentTimeMillis() : cryptoSeed.getBirthdate().getTime());
        }

        private DeterministicSeed getSeed(CryptoBip39 cryptoBip39) {
            return new DeterministicSeed(cryptoBip39.getWords(), null, System.currentTimeMillis(), DeterministicSeed.Type.BIP39);
        }

        private OutputDescriptor getOutputDescriptor(UROutputDescriptor urOutputDescriptor) {
            String source = urOutputDescriptor.getSource();
            List<RegistryItem> keys = urOutputDescriptor.getKeys();
            Map<ExtendedKey, String> mapExtendedPublicKeyLabels = new LinkedHashMap<>();

            for(int i = 0; i < keys.size(); i++) {
                RegistryItem key = keys.get(i);
                if(key instanceof URHDKey urhdKey) {
                    ExtendedKey extendedKey = getExtendedKey(urhdKey);
                    KeyDerivation keyDerivation = getKeyDerivation(urhdKey.getOrigin());
                    source = source.replaceAll("@" + i, OutputDescriptor.writeKey(extendedKey, keyDerivation, null, true, true));
                    if(urhdKey.getName() != null) {
                        mapExtendedPublicKeyLabels.put(extendedKey, urhdKey.getName());
                    }
                } else {
                    throw new IllegalArgumentException("Only extended HD keys are supported in output descriptors");
                }
            }

            return OutputDescriptor.getOutputDescriptor(source, mapExtendedPublicKeyLabels);
        }

        private List<Wallet> getWallets(URAccountDescriptor urAccountDescriptor) {
            List<Wallet> wallets = new ArrayList<>();
            String masterFingerprint = Utils.bytesToHex(urAccountDescriptor.getMasterFingerprint());
            for(UROutputDescriptor urOutputDescriptor : urAccountDescriptor.getOutputDescriptors()) {
                OutputDescriptor outputDescriptor = getOutputDescriptor(urOutputDescriptor);
                Wallet wallet = outputDescriptor.toKeystoreWallet(masterFingerprint);
                wallets.add(wallet);
            }

            return wallets;
        }

        private Result extractResultFromBBQR(BBQRDecoder.Result result) {
            if(result.getPsbt() != null) {
                return new Result(result.getPsbt());
            } else if(result.getTransaction() != null) {
                return new Result(result.getTransaction());
            } else if(result.toString() != null) {
                return new Result(result.toString());
            } else {
                log.error("Unsupported BBQR type " + result.getBbqrType());
                return new Result(new URException("BBQR type " + result.getBbqrType() + " is not supported"));
            }
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
                webcamService.setDevice(webcamDeviceProperty.get());
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
            Node button = null;
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

                button = hd;
            } else if(buttonType.getButtonData() == ButtonBar.ButtonData.HELP_2) {
                ComboBox<WebcamDevice> devicesCombo = new ComboBox<>(WebcamScanDriver.getFoundDevices());
                devicesCombo.setConverter(new StringConverter<>() {
                    @Override
                    public String toString(WebcamDevice device) {
                        return device instanceof WebcamScanDevice ? ((WebcamScanDevice)device).getDeviceName() : "Default Camera";
                    }

                    @Override
                    public WebcamDevice fromString(String string) {
                        throw new UnsupportedOperationException();
                    }
                });
                devicesCombo.valueProperty().bindBidirectional(webcamDeviceProperty);
                ButtonBar.setButtonData(devicesCombo, ButtonBar.ButtonData.LEFT);

                button = devicesCombo;
            } else {
                button = super.createButton(buttonType);
            }

            if(button instanceof Region) {
                ((Region)button).setPrefWidth(150);
                ((Region)button).setMaxWidth(150);
            }

            button.disableProperty().bind(webcamService.openingProperty());
            return button;
        }

        private void setHdGraphic(ToggleButton hd, boolean isHd) {
            if(isHd) {
                hd.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE));
            } else {
                hd.setGraphic(getGlyph(FontAwesome5.Glyph.BAN));
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
        public final String extendedKeyName;
        public final OutputDescriptor outputDescriptor;
        public final List<Wallet> wallets;
        public final DeterministicSeed seed;
        public final String payload;
        public final Throwable exception;

        public Result(Transaction transaction) {
            this.transaction = transaction;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(PSBT psbt) {
            this.transaction = null;
            this.psbt = psbt;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(BitcoinURI uri) {
            this.transaction = null;
            this.psbt = null;
            this.uri = uri;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(Address address) {
            this.transaction = null;
            this.psbt = null;
            this.uri = BitcoinURI.fromAddress(address);
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(ExtendedKey extendedKey, String name) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = extendedKey;
            this.extendedKeyName = name;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(OutputDescriptor outputDescriptor) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = outputDescriptor;
            this.wallets = null;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(List<Wallet> wallets) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = wallets;
            this.seed = null;
            this.payload = null;
            this.exception = null;
        }

        public Result(DeterministicSeed seed) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = seed;
            this.payload = null;
            this.exception = null;
        }

        public Result(String payload) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
            this.payload = payload;
            this.exception = null;
        }

        public Result(Throwable exception) {
            this.transaction = null;
            this.psbt = null;
            this.uri = null;
            this.extendedKey = null;
            this.extendedKeyName = null;
            this.outputDescriptor = null;
            this.wallets = null;
            this.seed = null;
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

    public static class WebcamDependencyException extends ScanException {
        public WebcamDependencyException() {
            super();
        }

        public WebcamDependencyException(String message) {
            super(message);
        }

        public WebcamDependencyException(Throwable cause) {
            super(cause);
        }

        public WebcamDependencyException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class WebcamOpenException extends ScanException {
        public WebcamOpenException() {
            super();
        }

        public WebcamOpenException(String message) {
            super(message);
        }

        public WebcamOpenException(Throwable cause) {
            super(cause);
        }

        public WebcamOpenException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ScanDelayCalculator implements WebcamUpdater.DelayCalculator {
        public long calculateDelay(long snapshotDuration, double deviceFps) {
            return Math.max(SCAN_PERIOD_MILLIS - snapshotDuration, 0L);
        }
    }
}
