package com.sparrowwallet.sparrow.io;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.*;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.psbt.PSBT;
import com.sparrowwallet.drongo.psbt.PSBTParseException;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.WalletModel;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Hwi {
    private static final Logger log = LoggerFactory.getLogger(Hwi.class);
    private static final String HWI_HOME_DIR = "hwi";
    private static final String HWI_VERSION_PREFIX = "hwi-";
    private static final String HWI_VERSION = "2.0.2";
    private static final String HWI_VERSION_DIR = HWI_VERSION_PREFIX + HWI_VERSION;

    private static boolean isPromptActive = false;

    public List<Device> enumerate(String passphrase) throws ImportException {
        String output = null;
        try {
            List<String> command;
            if(passphrase != null && !passphrase.isEmpty()) {
                command = List.of(getHwiExecutable(Command.ENUMERATE).getAbsolutePath(), "--password", escape(passphrase), Command.ENUMERATE.toString());
            } else {
                command = List.of(getHwiExecutable(Command.ENUMERATE).getAbsolutePath(), Command.ENUMERATE.toString());
            }

            isPromptActive = true;
            output = execute(command);
            Device[] devices = getGson().fromJson(output, Device[].class);
            if(devices == null) {
                throw new ImportException("Error scanning, check devices are ready");
            }
            return Arrays.stream(devices).filter(device -> device != null && device.getModel() != null).collect(Collectors.toList());
        } catch(IOException e) {
            log.error("Error executing " + HWI_VERSION_DIR, e);
            throw new ImportException("Error executing HWI", e);
        } catch(Exception e) {
            if(output != null) {
                try {
                    JsonObject result = JsonParser.parseString(output).getAsJsonObject();
                    JsonElement error = result.get("error");
                    throw new ImportException(error.getAsString());
                } catch(Exception ex) {
                    log.error("Error parsing JSON: " + output, e);
                    throw new ImportException("Error parsing JSON: " + output, e);
                }
            }
            throw e;
        } finally {
            isPromptActive = false;
        }
    }

    public boolean promptPin(Device device) throws ImportException {
        try {
            String output = execute(getDeviceCommand(device, Command.PROMPT_PIN));
            isPromptActive = true;
            return wasSuccessful(output);
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public boolean sendPin(Device device, String pin) throws ImportException {
        try {
            String output = execute(getDeviceCommand(device, Command.SEND_PIN, pin));
            isPromptActive = false;
            return wasSuccessful(output);
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public boolean togglePassphrase(Device device) throws ImportException {
        try {
            String output = execute(getDeviceCommand(device, Command.TOGGLE_PASSPHRASE));
            isPromptActive = false;
            return wasSuccessful(output);
        } catch(IOException e) {
            throw new ImportException(e);
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
            String output;
            if(passphrase != null && !passphrase.isEmpty() && device.getModel().externalPassphraseEntry()) {
                output = execute(getDeviceCommand(device, passphrase, Command.GET_XPUB, derivationPath));
            } else {
                output = execute(getDeviceCommand(device, Command.GET_XPUB, derivationPath));
            }

            isPromptActive = false;
            JsonObject result = JsonParser.parseString(output).getAsJsonObject();
            if(result.get("xpub") != null) {
                return result.get("xpub").getAsString();
            } else {
                JsonElement error = result.get("error");
                if(error != null) {
                    throw new ImportException(error.getAsString());
                } else {
                    throw new ImportException("Could not retrieve xpub - reconnect your device and try again.");
                }
            }
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public String displayAddress(Device device, String passphrase, ScriptType scriptType, OutputDescriptor outputDescriptor) throws DisplayAddressException {
        try {
            if(!Arrays.asList(ScriptType.ADDRESSABLE_TYPES).contains(scriptType)) {
                throw new IllegalArgumentException("Cannot display address for script type " + scriptType + ": Only addressable types supported");
            }

            String descriptor = outputDescriptor.toString();

            isPromptActive = true;
            String output;
            if(passphrase != null && !passphrase.isEmpty() && device.getModel().externalPassphraseEntry()) {
                output = execute(getDeviceCommand(device, passphrase, Command.DISPLAY_ADDRESS, "--desc", descriptor));
            } else {
                output = execute(getDeviceCommand(device, Command.DISPLAY_ADDRESS, "--desc", descriptor));
            }

            JsonObject result = JsonParser.parseString(output).getAsJsonObject();
            if(result.get("address") != null) {
                return result.get("address").getAsString();
            } else {
                JsonElement error = result.get("error");
                if(error != null) {
                    throw new DisplayAddressException(error.getAsString());
                } else {
                    throw new DisplayAddressException("Could not retrieve address");
                }
            }
        } catch(IOException e) {
            throw new DisplayAddressException(e);
        } finally {
            isPromptActive = false;
        }
    }

    public String signMessage(Device device, String passphrase, String message, String derivationPath) throws SignMessageException {
        try {
            isPromptActive = true;
            String output;
            if(passphrase != null && !passphrase.isEmpty() && device.getModel().externalPassphraseEntry()) {
                output = execute(getDeviceCommand(device, passphrase, Command.SIGN_MESSAGE, message, derivationPath));
            } else {
                output = execute(getDeviceCommand(device, Command.SIGN_MESSAGE, message, derivationPath));
            }

            JsonObject result = JsonParser.parseString(output).getAsJsonObject();
            if(result.get("signature") != null) {
                return result.get("signature").getAsString();
            } else {
                JsonElement error = result.get("error");
                if(error != null) {
                    throw new SignMessageException(error.getAsString());
                } else {
                    throw new SignMessageException("Could not sign message");
                }
            }
        } catch(IOException e) {
            throw new SignMessageException(e);
        } finally {
            isPromptActive = false;
        }
    }

    public PSBT signPSBT(Device device, String passphrase, PSBT psbt) throws SignTransactionException {
        try {
            String psbtBase64 = psbt.toBase64String();

            isPromptActive = true;
            String output;
            if(passphrase != null && !passphrase.isEmpty() && device.getModel().externalPassphraseEntry()) {
                output = execute(getDeviceCommand(device, passphrase, Command.SIGN_TX, psbtBase64));
            } else {
                output = execute(getDeviceCommand(device, Command.SIGN_TX, psbtBase64));
            }

            JsonObject result = JsonParser.parseString(output).getAsJsonObject();
            if(result.get("psbt") != null) {
                String strPsbt = result.get("psbt").getAsString();
                return PSBT.fromString(strPsbt);
            } else {
                JsonElement error = result.get("error");
                if(error != null && error.getAsString().equals("sign_tx canceled")) {
                    throw new SignTransactionException("Signing cancelled");
                } else if(error != null && error.getAsString().equals("open failed")) {
                    throw new SignTransactionException("Please reconnect your USB device");
                } else if(error != null) {
                    throw new SignTransactionException(error.getAsString());
                } else {
                    throw new SignTransactionException("Could not retrieve PSBT");
                }
            }
        } catch(IOException e) {
            throw new SignTransactionException("Could not sign PSBT", e);
        } catch(PSBTParseException e) {
            throw new SignTransactionException("Could not parse signed PSBT", e);
        } finally {
            isPromptActive = false;
        }
    }

    private String execute(List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        return CharStreams.toString(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    private synchronized File getHwiExecutable(Command command) {
        File hwiExecutable = Config.get().getHwi();
        if(hwiExecutable != null && hwiExecutable.exists()) {
            File homeDir = getHwiHomeDir();
            String tmpDir = System.getProperty("java.io.tmpdir");
            String hwiPath = hwiExecutable.getAbsolutePath();
            if(command.isTestFirst() && (hwiPath.contains(tmpDir) || hwiPath.startsWith(homeDir.getAbsolutePath())) && (!hwiPath.contains(HWI_VERSION_DIR) || !testHwi(hwiExecutable))) {
                if(Platform.getCurrent() == Platform.OSX) {
                    IOUtils.deleteDirectory(hwiExecutable.getParentFile());
                } else {
                    hwiExecutable.delete();
                }
            }
        }

        if(hwiExecutable == null || !hwiExecutable.exists()) {
            try {
                Platform platform = Platform.getCurrent();
                Set<PosixFilePermission> ownerExecutableWritable = PosixFilePermissions.fromString("rwxr--r--");

                //A PyInstaller --onefile expands into a new directory on every run triggering OSX Gatekeeper checks.
                //To avoid doing these with every invocation, use a --onedir packaging and expand into a temp folder on OSX
                //The check will still happen on first invocation, but will not thereafter
                //See https://github.com/bitcoin-core/HWI/issues/327 for details
                if(platform == Platform.OSX) {
                    InputStream inputStream = Hwi.class.getResourceAsStream("/native/osx/x64/" + HWI_VERSION_DIR + "-mac-amd64-signed.zip");
                    if(inputStream == null) {
                        throw new IllegalStateException("Cannot load " + HWI_VERSION_DIR + " from classpath");
                    }

                    File hwiHomeDir = getHwiHomeDir();
                    File hwiVersionDir = new File(hwiHomeDir, HWI_VERSION_DIR);
                    IOUtils.deleteDirectory(hwiVersionDir);
                    if(!hwiVersionDir.exists()) {
                        Files.createDirectories(hwiVersionDir.toPath(), PosixFilePermissions.asFileAttribute(ownerExecutableWritable));
                    }

                    log.debug("Using HWI path: " + hwiVersionDir.getAbsolutePath());

                    File hwiExec = null;
                    try(ZipInputStream zis = new ZipInputStream(inputStream)) {
                        ZipEntry zipEntry = zis.getNextEntry();
                        while(zipEntry != null) {
                            if(zipEntry.isDirectory()) {
                                newDirectory(hwiVersionDir, zipEntry, ownerExecutableWritable);
                            } else {
                                File newFile = newFile(hwiVersionDir, zipEntry, ownerExecutableWritable);
                                try(FileOutputStream fos = new FileOutputStream(newFile)) {
                                    ByteStreams.copy(zis, new FileOutputStream(newFile));
                                    fos.flush();
                                };

                                if(zipEntry.getName().equals("hwi")) {
                                    hwiExec = newFile;
                                }
                            }

                            zipEntry = zis.getNextEntry();
                        }
                        zis.closeEntry();
                    }

                    hwiExecutable = hwiExec;
                } else {
                    InputStream inputStream;
                    Path tempExecPath;
                    if(platform == Platform.WINDOWS) {
                        inputStream = Hwi.class.getResourceAsStream("/native/windows/x64/hwi.exe");
                        tempExecPath = Files.createTempFile(HWI_VERSION_DIR, null);
                    } else {
                        inputStream = Hwi.class.getResourceAsStream("/native/linux/x64/hwi");
                        tempExecPath = Files.createTempFile(HWI_VERSION_DIR, null, PosixFilePermissions.asFileAttribute(ownerExecutableWritable));
                    }

                    if(inputStream == null) {
                        throw new IllegalStateException("Cannot load " + HWI_VERSION_DIR + " from classpath");
                    }

                    File tempExec = tempExecPath.toFile();
                    tempExec.deleteOnExit();
                    try(OutputStream tempExecStream = new BufferedOutputStream(new FileOutputStream(tempExec))) {
                        ByteStreams.copy(inputStream, tempExecStream);
                        inputStream.close();
                        tempExecStream.flush();
                    };

                    hwiExecutable = tempExec;
                }
            } catch(Exception e) {
                log.error("Error initializing HWI", e);
            }

            Config.get().setHwi(hwiExecutable);
        }

        return hwiExecutable;
    }

    private File getHwiHomeDir() {
        if(Platform.getCurrent() == Platform.OSX) {
            return new File(Storage.getSparrowDir(), HWI_HOME_DIR);
        }

        return new File(System.getProperty("java.io.tmpdir"));
    }

    private boolean testHwi(File hwiExecutable) {
        try {
            List<String> command = List.of(hwiExecutable.getAbsolutePath(), "--version");
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            int exitValue = process.waitFor();
            return exitValue == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static File newDirectory(File destinationDir, ZipEntry zipEntry, Set<PosixFilePermission> setFilePermissions) throws IOException {
        String destDirPath = destinationDir.getCanonicalPath();

        Path path = Path.of(destDirPath, zipEntry.getName());
        File destDir = Files.createDirectory(path, PosixFilePermissions.asFileAttribute(setFilePermissions)).toFile();

        String destSubDirPath = destDir.getCanonicalPath();
        if(!destSubDirPath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destDir;
    }

    public static File newFile(File destinationDir, ZipEntry zipEntry, Set<PosixFilePermission> setFilePermissions) throws IOException {
        String destDirPath = destinationDir.getCanonicalPath();

        Path path = Path.of(destDirPath, zipEntry.getName());
        File destFile = Files.createFile(path, PosixFilePermissions.asFileAttribute(setFilePermissions)).toFile();

        String destFilePath = destFile.getCanonicalPath();
        if(!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }

    private boolean wasSuccessful(String output) throws ImportException {
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();
        if(result.get("error") != null) {
            throw new ImportException(result.get("error").getAsString());
        }

        return result.get("success").getAsBoolean();
    }

    private List<String> getDeviceCommand(Device device, Command command) throws IOException {
        List<String> elements = new ArrayList<>(List.of(getHwiExecutable(command).getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), command.toString()));
        addChainType(elements);
        return elements;
    }

    private List<String> getDeviceCommand(Device device, Command command, String... commandData) throws IOException {
        List<String> elements = new ArrayList<>(List.of(getHwiExecutable(command).getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), command.toString()));
        addChainType(elements);
        elements.addAll(Arrays.stream(commandData).filter(Objects::nonNull).collect(Collectors.toList()));
        return elements;
    }

    private List<String> getDeviceCommand(Device device, String passphrase, Command command, String... commandData) throws IOException {
        List<String> elements = new ArrayList<>(List.of(getHwiExecutable(command).getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), "--password", escape(passphrase), command.toString()));
        addChainType(elements);
        elements.addAll(Arrays.stream(commandData).filter(Objects::nonNull).collect(Collectors.toList()));
        return elements;
    }

    private void addChainType(List<String> elements) {
        if(Network.get() != Network.MAINNET) {
            elements.add(elements.size() - 1, "--chain");
            elements.add(elements.size() - 1, getChainName(Network.get()));
        }
    }

    private String getChainName(Network network) {
        if(network == Network.MAINNET) {
            return "main";
        } else if(network == Network.TESTNET) {
            return "test";
        }

        return network.toString();
    }

    private String escape(String passphrase) {
        Platform platform = Platform.getCurrent();
        if(platform == Platform.WINDOWS) {
            return passphrase.replace("\"", "\\\"");
        }

        return passphrase;
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
        private final OutputDescriptor outputDescriptor;

        public DisplayAddressService(Device device, String passphrase, ScriptType scriptType, OutputDescriptor outputDescriptor) {
            this.device = device;
            this.passphrase = passphrase;
            this.scriptType = scriptType;
            this.outputDescriptor = outputDescriptor;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws DisplayAddressException {
                    Hwi hwi = new Hwi();
                    return hwi.displayAddress(device, passphrase, scriptType, outputDescriptor);
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

        public SignPSBTService(Device device, String passphrase, PSBT psbt) {
            this.device = device;
            this.passphrase = passphrase;
            this.psbt = psbt;
        }

        @Override
        protected Task<PSBT> createTask() {
            return new Task<>() {
                protected PSBT call() throws SignTransactionException {
                    Hwi hwi = new Hwi();
                    return hwi.signPSBT(device, passphrase, psbt);
                }
            };
        }
    }

    public Gson getGson() {
        GsonBuilder gsonBuilder = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        gsonBuilder.registerTypeAdapter(WalletModel.class, new DeviceModelSerializer());
        gsonBuilder.registerTypeAdapter(WalletModel.class, new DeviceModelDeserializer());
        return gsonBuilder.create();
    }

    private static class DeviceModelSerializer implements JsonSerializer<WalletModel> {
        @Override
        public JsonElement serialize(WalletModel src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.toString().toLowerCase());
        }
    }

    private static class DeviceModelDeserializer implements JsonDeserializer<WalletModel> {
        @Override
        public WalletModel deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            String modelStr = json.getAsJsonPrimitive().getAsString();
            try {
                return WalletModel.valueOf(modelStr.toUpperCase());
            } catch(Exception e) {
                for(WalletModel model : WalletModel.values()) {
                    if(modelStr.startsWith(model.getType())) {
                        return model;
                    }
                }
            }
            throw new IllegalArgumentException("Could not determine wallet model for " + modelStr);
        }
    }

    private enum Command {
        ENUMERATE("enumerate", true),
        PROMPT_PIN("promptpin", true),
        SEND_PIN("sendpin", false),
        TOGGLE_PASSPHRASE("togglepassphrase", true),
        DISPLAY_ADDRESS("displayaddress", true),
        SIGN_MESSAGE("signmessage", true),
        GET_XPUB("getxpub", true),
        SIGN_TX("signtx", true);

        private final String command;
        private final boolean testFirst;

        Command(String command, boolean testFirst) {
            this.command = command;
            this.testFirst = testFirst;
        }

        public String getCommand() {
            return command;
        }

        public boolean isTestFirst() {
            return testFirst;
        }

        @Override
        public String toString() {
            return command;
        }
    }
}
