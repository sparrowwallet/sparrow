package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.IOUtils;
import com.sparrowwallet.drongo.OsType;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.lark.DeviceException;
import com.sparrowwallet.lark.Lark;
import com.sparrowwallet.lark.bitbox02.BitBoxFileNoiseConfig;
import com.sparrowwallet.lark.trezor.TrezorFileNoiseConfig;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.control.BitBoxPairingDialog;
import com.sparrowwallet.sparrow.control.TextfieldDialog;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.smartcardio.CardException;
import javax.smartcardio.CardNotPresentException;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Hwi {
    private static final Logger log = LoggerFactory.getLogger(Hwi.class);
    private static final String HWI_HOME_DIR = "hwi";
    private static final String LARK_HOME_DIR = "lark";
    private static final String BITBOX_FILENAME = "bitbox02.json";
    private static final String TREZOR_FILENAME = "trezor.json";

    private static volatile boolean isPromptActive = false;

    private final Set<byte[]> newDeviceRegistrations = new HashSet<>();

    static {
        deleteHwiDir();
    }

    public List<Device> enumerate(String passphrase) throws ImportException {
        List<Device> devices = new ArrayList<>();
        devices.addAll(enumerateUsb(passphrase));
        devices.addAll(enumerateCard());
        return devices;
    }

    private List<Device> enumerateUsb(String passphrase) throws ImportException {
        try {
            Lark lark = getLark(passphrase);
            isPromptActive = true;
            return lark.enumerate().stream().map(Device::fromHardwareClient).toList();
        } catch(Throwable e) {
            log.error("Error enumerating USB devices", e);
            throw new ImportException(e.getMessage() == null || e.getMessage().isEmpty() ? "Error scanning, check devices are ready" : e.getMessage(), e);
        } finally {
            isPromptActive = false;
        }
    }

    private List<Device> enumerateCard() {
        List<Device> devices = new ArrayList<>();
        if(CardApi.isReaderAvailable()) {
            try {
                List<WalletModel> connectedCards = CardApi.getConnectedCards();
                for(WalletModel card : connectedCards) {
                    CardApi cardApi = CardApi.getCardApi(card, null);
                    WalletModel walletModel = cardApi.getCardType();

                    Device cardDevice = new Device();
                    cardDevice.setType(walletModel.getType());
                    cardDevice.setModel(walletModel);
                    cardDevice.setNeedsPassphraseSent(Boolean.FALSE);
                    cardDevice.setNeedsPinSent(Boolean.FALSE);
                    cardDevice.setCard(true);
                    devices.add(cardDevice);
                }
            } catch(CardNotPresentException e) {
                //ignore
            } catch(CardException e) {
                log.error("Error reading card", e);
            }
        }

        return devices;
    }

    public boolean promptPin(Device device) throws ImportException {
        try {
            Lark lark = getLark();
            boolean result = lark.promptPin(device.getType(), device.getPath());
            isPromptActive = true;
            return result;
        } catch(DeviceException e) {
            throw new ImportException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error prompting pin", e);
            throw e;
        }
    }

    public boolean sendPin(Device device, String pin) throws ImportException {
        try {
            Lark lark = getLark();
            boolean result = lark.sendPin(device.getType(), device.getPath(), pin);
            isPromptActive = false;
            return result;
        } catch(DeviceException e) {
            throw new ImportException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error sending pin", e);
            throw e;
        }
    }

    public boolean togglePassphrase(Device device) throws ImportException {
        try {
            Lark lark = getLark();
            boolean result = lark.togglePassphrase(device.getType(), device.getPath());
            isPromptActive = false;
            return result;
        } catch(DeviceException e) {
            throw new ImportException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error toggling passphrase", e);
            throw e;
        }
    }

    public Map<StandardAccount, String> getXpubs(Device device, String passphrase, Map<StandardAccount, String> accountDerivationPaths) throws ImportException {
        Map<StandardAccount, String> accountXpubs = new LinkedHashMap<>();
        for(Map.Entry<StandardAccount, String> entry : accountDerivationPaths.entrySet()) {
            accountXpubs.put(entry.getKey(), getXpub(device, passphrase, entry.getValue()));
        }

        return accountXpubs;
    }

    public String getXpub(Device device, String passphrase, String derivationPath) throws ImportException {
        try {
            Lark lark = getLark(passphrase);
            ExtendedKey xpub = lark.getPubKeyAtPath(device.getType(), device.getPath(), derivationPath);
            isPromptActive = false;
            return xpub.toString();
        } catch(DeviceException e) {
            throw new ImportException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error retrieving xpub", e);
            throw e;
        }
    }

    public String displayAddress(Device device, String passphrase, ScriptType scriptType, OutputDescriptor addressDescriptor,
                                 OutputDescriptor walletDescriptor, String walletName, byte[] walletRegistration) throws DisplayAddressException {
        try {
            if(!Arrays.asList(ScriptType.ADDRESSABLE_TYPES).contains(scriptType)) {
                throw new IllegalArgumentException("Cannot display address for script type " + scriptType + ": Only addressable types supported");
            }

            isPromptActive = true;
            Lark lark = getLark(passphrase, walletDescriptor, walletName, walletRegistration);
            String address = lark.displayAddress(device.getType(), device.getPath(), addressDescriptor);
            newDeviceRegistrations.addAll(lark.getWalletRegistrations().values());
            newDeviceRegistrations.remove(walletRegistration);
            return address;
        } catch(DeviceException e) {
            throw new DisplayAddressException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error displaying address", e);
            throw e;
        } finally {
            isPromptActive = false;
        }
    }

    public String signMessage(Device device, String passphrase, String message, String derivationPath) throws SignMessageException {
        try {
            isPromptActive = true;
            Lark lark = getLark(passphrase);
            return lark.signMessage(device.getType(), device.getPath(), message, derivationPath);
        } catch(DeviceException e) {
            throw new SignMessageException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error signing message", e);
            throw e;
        } finally {
            isPromptActive = false;
        }
    }

    public PSBT signPSBT(Device device, String passphrase, PSBT psbt,
                         OutputDescriptor walletDescriptor, String walletName, byte[] walletRegistration) throws SignTransactionException {
        try {
            isPromptActive = true;
            Lark lark = getLark(passphrase, walletDescriptor, walletName, walletRegistration);
            PSBT signed = lark.signTransaction(device.getType(), device.getPath(), psbt);
            newDeviceRegistrations.addAll(lark.getWalletRegistrations().values());
            newDeviceRegistrations.remove(walletRegistration);
            return signed;
        } catch(DeviceException e) {
            throw new SignTransactionException(e.getMessage(), e);
        } catch(RuntimeException e) {
            log.error("Error signing PSBT", e);
            throw e;
        } finally {
            isPromptActive = false;
        }
    }

    private Lark getLark() {
        return getLark(null);
    }

    private Lark getLark(String passphrase) {
        return getLark(passphrase, null, null, null);
    }

    private Lark getLark(String passphrase, OutputDescriptor walletDescriptor, String walletName, byte[] walletRegistration) {
        Lark lark = new Lark(AppServices.getHttpClientService());
        lark.setBitBoxNoiseConfig(new BitBoxFxNoiseConfig());
        lark.setTrezorNoiseConfig(new TrezorFxNoiseConfig());
        if(passphrase != null) {
            lark.setPassphrase(passphrase);
        }

        if(walletDescriptor != null && walletName != null) {
            if(walletRegistration != null) {
                lark.addWalletRegistration(walletDescriptor, walletName, walletRegistration);
            } else {
                lark.addWalletName(walletDescriptor, walletName);
            }
        }

        return lark;
    }

    private static void deleteHwiDir() {
        try {
            if(OsType.getCurrent() == OsType.MACOS || OsType.getCurrent() == OsType.WINDOWS) {
                File hwiHomeDir = new File(Storage.getSparrowDir(), HWI_HOME_DIR);
                if(hwiHomeDir.exists()) {
                    IOUtils.deleteDirectory(hwiHomeDir);
                }
            }
        } catch(Exception e) {
            log.error("Error deleting hwi directory", e);
        }
    }

    public static class EnumerateService extends Service<List<Device>> {
        private final String passphrase;

        public EnumerateService(String passphrase) {
            this.passphrase = passphrase;
        }

        @Override
        protected Task<List<Device>> createTask() {
            return new Task<>() {
                protected List<Device> call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.enumerate(passphrase);
                }
            };
        }
    }

    public static class ScheduledEnumerateService extends ScheduledService<List<Device>> {
        private final String passphrase;

        public ScheduledEnumerateService(String passphrase) {
            this.passphrase = passphrase;
        }

        @Override
        protected Task<List<Device>> createTask() {
            return new Task<>() {
                protected List<Device> call() throws ImportException {
                    if(!isPromptActive) {
                        Hwi hwi = new Hwi();
                        return hwi.enumerate(passphrase);
                    }

                    return null;
                }
            };
        }
    }

    public static class PromptPinService extends Service<Boolean> {
        private final Device device;

        public PromptPinService(Device device) {
            this.device = device;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.promptPin(device);
                }
            };
        }
    }

    public static class SendPinService extends Service<Boolean> {
        private final Device device;
        private final String pin;

        public SendPinService(Device device, String pin) {
            this.device = device;
            this.pin = pin;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.sendPin(device, pin);
                }
            };
        }
    }

    public static class TogglePassphraseService extends Service<Boolean> {
        private final Device device;

        public TogglePassphraseService(Device device) {
            this.device = device;
        }

        @Override
        protected Task<Boolean> createTask() {
            return new Task<>() {
                protected Boolean call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.togglePassphrase(device);
                }
            };
        }
    }

    public static class DisplayAddressService extends Service<String> {
        private final Device device;
        private final String passphrase;
        private final ScriptType scriptType;
        private final OutputDescriptor addressDescriptor;
        private final OutputDescriptor walletDescriptor;
        private final String walletName;
        private final byte[] walletRegistration;
        private final Set<byte[]> newDeviceRegistrations = new HashSet<>();

        public DisplayAddressService(Device device, String passphrase, ScriptType scriptType, OutputDescriptor addressDescriptor, OutputDescriptor walletDescriptor, String walletName, byte[] walletRegistration) {
            this.device = device;
            this.passphrase = passphrase;
            this.scriptType = scriptType;
            this.addressDescriptor = addressDescriptor;
            this.walletDescriptor = walletDescriptor;
            this.walletName = walletName;
            this.walletRegistration = walletRegistration;
        }

        public Set<byte[]> getNewDeviceRegistrations() {
            return newDeviceRegistrations;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws DisplayAddressException {
                    Hwi hwi = new Hwi();
                    String address = hwi.displayAddress(device, passphrase, scriptType, addressDescriptor, walletDescriptor, walletName, walletRegistration);
                    newDeviceRegistrations.addAll(hwi.newDeviceRegistrations);
                    return address;
                }
            };
        }
    }

    public static class SignMessageService extends Service<String> {
        private final Device device;
        private final String passphrase;
        private final String message;
        private final String derivationPath;

        public SignMessageService(Device device, String passphrase, String message, String derivationPath) {
            this.device = device;
            this.passphrase = passphrase;
            this.message = message;
            this.derivationPath = derivationPath;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws SignMessageException {
                    Hwi hwi = new Hwi();
                    return hwi.signMessage(device, passphrase, message, derivationPath);
                }
            };
        }
    }

    public static class GetXpubService extends Service<String> {
        private final Device device;
        private final String passphrase;
        private final String derivationPath;

        public GetXpubService(Device device, String passphrase, String derivationPath) {
            this.device = device;
            this.passphrase = passphrase;
            this.derivationPath = derivationPath;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.getXpub(device, passphrase, derivationPath);
                }
            };
        }
    }

    public static class GetXpubsService extends Service<Map<StandardAccount, String>> {
        private final Device device;
        private final String passphrase;
        private final Map<StandardAccount, String> accountDerivationPaths;

        public GetXpubsService(Device device, String passphrase, Map<StandardAccount, String> accountDerivationPaths) {
            this.device = device;
            this.passphrase = passphrase;
            this.accountDerivationPaths = accountDerivationPaths;
        }

        @Override
        protected Task<Map<StandardAccount, String>> createTask() {
            return new Task<>() {
                protected Map<StandardAccount, String> call() throws ImportException {
                    Hwi hwi = new Hwi();
                    return hwi.getXpubs(device, passphrase, accountDerivationPaths);
                }
            };
        }
    }

    public static class SignPSBTService extends Service<PSBT> {
        private final Device device;
        private final String passphrase;
        private final PSBT psbt;
        private final OutputDescriptor walletDescriptor;
        private final String walletName;
        private final byte[] walletRegistration;
        private final Set<byte[]> newDeviceRegistrations = new HashSet<>();

        public SignPSBTService(Device device, String passphrase, PSBT psbt, OutputDescriptor walletDescriptor, String walletName, byte[] walletRegistration) {
            this.device = device;
            this.passphrase = passphrase;
            this.psbt = psbt;
            this.walletDescriptor = walletDescriptor;
            this.walletName = walletName;
            this.walletRegistration = walletRegistration;
        }

        public Set<byte[]> getNewDeviceRegistrations() {
            return newDeviceRegistrations;
        }

        @Override
        protected Task<PSBT> createTask() {
            return new Task<>() {
                protected PSBT call() throws SignTransactionException {
                    Hwi hwi = new Hwi();
                    PSBT signed = hwi.signPSBT(device, passphrase, psbt, walletDescriptor, walletName, walletRegistration);
                    newDeviceRegistrations.addAll(hwi.newDeviceRegistrations);
                    return signed;
                }
            };
        }
    }

    private static final class BitBoxFxNoiseConfig extends BitBoxFileNoiseConfig {
        private BitBoxPairingDialog pairingDialog;

        public BitBoxFxNoiseConfig() {
            super(Path.of(Storage.getSparrowHome().getAbsolutePath(), LARK_HOME_DIR, BITBOX_FILENAME).toFile());
        }

        @Override
        public boolean showPairing(String code, DeviceResponse response) throws DeviceException {
            CountDownLatch latch = new CountDownLatch(1);
            AtomicBoolean confirmedDevice = new AtomicBoolean(false);

            Thread showPairingDeviceThread = new Thread(() -> {
                try {
                    isPromptActive = true;
                    confirmedDevice.set(response.call());
                    latch.countDown();
                } catch(DeviceException e) {
                    throw new RuntimeException(e);
                } finally {
                    isPromptActive = false;
                }
            });
            showPairingDeviceThread.start();

            Platform.runLater(() -> {
                pairingDialog = new BitBoxPairingDialog(code);
                pairingDialog.initOwner(AppServices.getActiveWindow());
                pairingDialog.show();
            });

            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Platform.runLater(() -> {
                if(pairingDialog != null && pairingDialog.isShowing()) {
                    pairingDialog.setResult(ButtonType.APPLY);
                }
                if(!confirmedDevice.get()) {
                    AppServices.showWarningDialog("Pairing Refused", "Pairing was refused on the device.");
                }
            });

            return confirmedDevice.get();
        }
    }

    private static final class TrezorFxNoiseConfig extends TrezorFileNoiseConfig {
        private String deviceInfo;

        public TrezorFxNoiseConfig() {
            super(Path.of(Storage.getSparrowHome().getAbsolutePath(), LARK_HOME_DIR, TREZOR_FILENAME).toFile());
        }

        @Override
        public String promptForPairingCode() {
            CompletableFuture<String> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                TextfieldDialog textfieldDialog = new TextfieldDialog();
                textfieldDialog.initOwner(AppServices.getActiveWindow());
                textfieldDialog.setTitle("Enter Pairing Code");
                textfieldDialog.setHeaderText("Enter the code shown on the " + deviceInfo + ":");
                textfieldDialog.getDialogPane().setPrefWidth(300);
                textfieldDialog.getEditor().setOnAction(_ -> textfieldDialog.setResult(textfieldDialog.getEditor().getText()));
                textfieldDialog.getEditor().setTextFormatter(new TextFormatter<>(change -> {
                    String newText = change.getControlNewText();
                    if(newText.matches("\\d*")) {
                        return change;
                    }
                    return null;
                }));
                textfieldDialog.getEditor().setStyle("-fx-font-size: 30px;");
                HBox.setMargin(textfieldDialog.getEditor(), new Insets(0, 65, 0, 65));
                textfieldDialog.getEditor().requestFocus();
                textfieldDialog.showAndWait().ifPresentOrElse(future::complete, () -> future.complete(null));
            });

            try {
                isPromptActive = true;
                return future.get(); // Block until dialog is closed
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                return null;
            } finally {
                isPromptActive = false;
            }
        }

        @Override
        public boolean confirmPairing(String deviceInfo) {
            this.deviceInfo = deviceInfo;
            CompletableFuture<ButtonType> future = new CompletableFuture<>();
            Platform.runLater(() -> {
                AppServices.showAlertDialog("Pairing Required", "Pair the " + deviceInfo + " with " + SparrowWallet.APP_NAME + "?",
                        Alert.AlertType.CONFIRMATION, ButtonType.YES, ButtonType.NO).ifPresentOrElse(future::complete, () -> future.complete(null));
            });

            try {
                isPromptActive = true;
                return future.get() == ButtonType.YES; // Block until dialog is closed
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                return false;
            } finally {
                isPromptActive = false;
            }
        }

        @Override
        public void displayPairingCode(String code) {
            super.displayPairingCode(code);
        }

        @Override
        public String getAppName() {
            return SparrowWallet.APP_NAME;
        }

        @Override
        public void pairingFailed(String reason) {
            Platform.runLater(() -> AppServices.showErrorDialog("Pairing Failed", "Pairing failed: " + reason));
        }

        @Override
        public void pairingSuccessful(String deviceInfo) {
            this.deviceInfo = deviceInfo;
            Platform.runLater(() -> AppServices.showSuccessDialog("Pairing Successful", "The " + deviceInfo + " has been successfully paired."));
        }
    }
}
