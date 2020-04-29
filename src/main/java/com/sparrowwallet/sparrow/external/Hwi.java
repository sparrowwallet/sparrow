package com.sparrowwallet.sparrow.external;

import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.*;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.WalletModel;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream;
import org.controlsfx.tools.Platform;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Hwi implements KeystoreImport {
    private static File hwiExecutable;

    @Override
    public String getName() {
        return "Hardware Wallet";
    }

    @Override
    public String getKeystoreImportDescription() {
        return "Imports a connected hardware wallet";
    }

    @Override
    public PolicyType getPolicyType() {
        return PolicyType.SINGLE;
    }

    @Override
    public Keystore getKeystore(ScriptType scriptType, InputStream inputStream) throws ImportException {
        return null;
    }

    public List<Device> enumerate(String passphrase) throws ImportException {
        try {
            List<String> command;
            if(passphrase != null) {
                command = List.of(getHwiExecutable().getAbsolutePath(), "--password", passphrase, "enumerate");
            } else {
                command = List.of(getHwiExecutable().getAbsolutePath(), "enumerate");
            }

            String output = execute(command);
            Device[] devices = getGson().fromJson(output, Device[].class);
            return Arrays.asList(devices);
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public boolean promptPin(Device device) throws ImportException {
        try {
            String output = execute(getDeviceCommand(device, "promptpin"));
            return wasSuccessful(output);
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public boolean sendPin(Device device, String pin) throws ImportException {
        try {
            String output = execute(getDeviceCommand(device, "sendpin", pin));
            return wasSuccessful(output);
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    public String getXpub(Device device, String passphrase, String derivationPath) throws ImportException {
        try {
            String output;
            if(passphrase != null && device.getModel().equals(WalletModel.TREZOR_1)) {
                output = execute(getDeviceCommand(device, passphrase, "getxpub", derivationPath));
            } else {
                output = execute(getDeviceCommand(device, "getxpub", derivationPath));
            }

            JsonObject result = JsonParser.parseString(output).getAsJsonObject();
            if(result.get("xpub") != null) {
                return result.get("xpub").getAsString();
            } else {
                throw new ImportException("Could not retrieve xpub");
            }
        } catch(IOException e) {
            throw new ImportException(e);
        }
    }

    private String execute(List<String> command) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();
        return CharStreams.toString(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    }

    private synchronized File getHwiExecutable() throws IOException {
        if(hwiExecutable == null) {
            Platform platform = Platform.getCurrent();
            System.out.println("/external/" + platform.getPlatformId().toLowerCase() + "/hwi");
            InputStream inputStream = Hwi.class.getResourceAsStream("/external/" + platform.getPlatformId().toLowerCase() + "/hwi");
            Set<PosixFilePermission> ownerExecutableWritable = PosixFilePermissions.fromString("rwxr--r--");
            Path tempExecPath = Files.createTempFile("hwi", null, PosixFilePermissions.asFileAttribute(ownerExecutableWritable));
            File tempExec = tempExecPath.toFile();
            System.out.println(tempExec.getAbsolutePath());
            tempExec.deleteOnExit();
            OutputStream tempExecStream = new BufferedOutputStream(new FileOutputStream(tempExec));
            ByteStreams.copy(new FramedLZ4CompressorInputStream(inputStream), tempExecStream);
            inputStream.close();
            tempExecStream.flush();
            tempExecStream.close();

            hwiExecutable = tempExec;
        }

        return hwiExecutable;
    }

    private boolean wasSuccessful(String output) throws ImportException {
        JsonObject result = JsonParser.parseString(output).getAsJsonObject();
        if(result.get("error") != null) {
            throw new ImportException(result.get("error").getAsString());
        }

        return result.get("success").getAsBoolean();
    }

    private List<String> getDeviceCommand(Device device, String command) throws IOException {
        return List.of(getHwiExecutable().getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), command);
    }

    private List<String> getDeviceCommand(Device device, String command, String data) throws IOException {
        return List.of(getHwiExecutable().getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), command, data);
    }

    private List<String> getDeviceCommand(Device device, String passphrase, String command, String data) throws IOException {
        return List.of(getHwiExecutable().getAbsolutePath(), "--device-path", device.getPath(), "--device-type", device.getType(), "--password", passphrase, command, data);
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

    public static class PromptPinService extends Service<Boolean> {
        private Device device;

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
        private Device device;
        private String pin;

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

    public static class GetXpubService extends Service<String> {
        private Device device;
        private String passphrase;
        private String derivationPath;

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
            return WalletModel.valueOf(json.getAsJsonPrimitive().getAsString().toUpperCase());
        }
    }
}
