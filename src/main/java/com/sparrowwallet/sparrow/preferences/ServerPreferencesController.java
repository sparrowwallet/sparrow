package com.sparrowwallet.sparrow.preferences;

import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.control.TextFieldValidator;
import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Form;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.List;

public class ServerPreferencesController extends PreferencesDetailController {
    private static final Logger log = LoggerFactory.getLogger(ServerPreferencesController.class);

    @FXML
    private ToggleGroup serverTypeToggleGroup;

    @FXML
    private Form coreForm;

    @FXML
    private TextField coreHost;

    @FXML
    private TextField corePort;

    @FXML
    private ToggleGroup coreAuthToggleGroup;

    @FXML
    private Field coreDataDirField;

    @FXML
    private TextField coreDataDir;

    @FXML
    private Button coreDataDirSelect;

    @FXML
    private Field coreUserPassField;

    @FXML
    private TextField coreUser;

    @FXML
    private PasswordField corePass;

    @FXML
    private UnlabeledToggleSwitch coreMultiWallet;

    @FXML
    private TextField coreWallet;

    @FXML
    private Form electrumForm;

    @FXML
    private TextField electrumHost;

    @FXML
    private TextField electrumPort;

    @FXML
    private UnlabeledToggleSwitch electrumUseSsl;

    @FXML
    private TextField electrumCertificate;

    @FXML
    private Button electrumCertificateSelect;

    @FXML
    private UnlabeledToggleSwitch useProxy;

    @FXML
    private TextField proxyHost;

    @FXML
    private TextField proxyPort;

    @FXML
    private Button testConnection;

    @FXML
    private Button editConnection;

    @FXML
    private TextArea testResults;

    private final ValidationSupport validationSupport = new ValidationSupport();

    private ElectrumServer.ConnectionService connectionService;

    @Override
    public void initializeView(Config config) {
        EventManager.get().register(this);
        getMasterController().closingProperty().addListener((observable, oldValue, newValue) -> {
            EventManager.get().unregister(this);
        });

        Platform.runLater(this::setupValidation);

        coreForm.managedProperty().bind(coreForm.visibleProperty());
        electrumForm.managedProperty().bind(electrumForm.visibleProperty());
        coreForm.visibleProperty().bind(electrumForm.visibleProperty().not());
        serverTypeToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(serverTypeToggleGroup.getSelectedToggle() != null) {
                ServerType serverType = (ServerType)newValue.getUserData();
                electrumForm.setVisible(serverType == ServerType.ELECTRUM_SERVER);
                config.setServerType(serverType);
                testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE, ""));
                testResults.clear();
                EventManager.get().post(new ServerTypeChangedEvent(serverType));
            } else if(oldValue != null) {
                oldValue.setSelected(true);
            }
        });
        ServerType serverType = config.getServerType() != null ? config.getServerType() : (config.getCoreServer() == null && config.getElectrumServer() != null ? ServerType.ELECTRUM_SERVER : ServerType.BITCOIN_CORE);
        serverTypeToggleGroup.selectToggle(serverTypeToggleGroup.getToggles().stream().filter(toggle -> toggle.getUserData() == serverType).findFirst().orElse(null));

        corePort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        electrumPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        proxyPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());

        coreHost.textProperty().addListener(getBitcoinCoreListener(config));
        corePort.textProperty().addListener(getBitcoinCoreListener(config));

        coreUser.textProperty().addListener(getBitcoinAuthListener(config));
        corePass.textProperty().addListener(getBitcoinAuthListener(config));

        coreWallet.textProperty().addListener(getBitcoinWalletListener(config));

        electrumHost.textProperty().addListener(getElectrumServerListener(config));
        electrumPort.textProperty().addListener(getElectrumServerListener(config));

        proxyHost.textProperty().addListener(getProxyListener(config));
        proxyPort.textProperty().addListener(getProxyListener(config));

        coreDataDirField.managedProperty().bind(coreDataDirField.visibleProperty());
        coreUserPassField.managedProperty().bind(coreUserPassField.visibleProperty());
        coreUserPassField.visibleProperty().bind(coreDataDirField.visibleProperty().not());
        coreAuthToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(coreAuthToggleGroup.getSelectedToggle() != null) {
                CoreAuthType coreAuthType = (CoreAuthType)newValue.getUserData();
                coreDataDirField.setVisible(coreAuthType == CoreAuthType.COOKIE);
                config.setCoreAuthType(coreAuthType);
            } else if(oldValue != null) {
                oldValue.setSelected(true);
            }
        });
        CoreAuthType coreAuthType = config.getCoreAuthType() != null ? config.getCoreAuthType() : CoreAuthType.COOKIE;
        coreAuthToggleGroup.selectToggle(coreAuthToggleGroup.getToggles().stream().filter(toggle -> toggle.getUserData() == coreAuthType).findFirst().orElse(null));

        coreDataDir.textProperty().addListener((observable, oldValue, newValue) -> {
            File dataDir = getDirectory(newValue);
            config.setCoreDataDir(dataDir);
        });

        coreDataDirSelect.setOnAction(event -> {
            Stage window = new Stage();

            DirectoryChooser directorChooser = new DirectoryChooser();
            directorChooser.setTitle("Select Bitcoin Core Data Directory");
            directorChooser.setInitialDirectory(config.getCoreDataDir() != null ? config.getCoreDataDir() : new File(System.getProperty("user.home")));

            File dataDir = directorChooser.showDialog(window);
            if(dataDir != null) {
                coreDataDir.setText(dataDir.getAbsolutePath());
            }
        });

        coreMultiWallet.selectedProperty().addListener((observable, oldValue, newValue) -> {
            coreWallet.setText(" ");
            coreWallet.setText("");
            coreWallet.setDisable(!newValue);
            coreWallet.setPromptText(newValue ? "" : "Default");
        });

        electrumUseSsl.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setElectrumServerInConfig(config);
            electrumCertificate.setDisable(!newValue);
            electrumCertificateSelect.setDisable(!newValue);
        });

        electrumCertificate.textProperty().addListener((observable, oldValue, newValue) -> {
            File crtFile = getCertificate(newValue);
            config.setElectrumServerCert(crtFile);
        });

        electrumCertificateSelect.setOnAction(event -> {
            Stage window = new Stage();

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Electrum Server certificate");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Files", org.controlsfx.tools.Platform.getCurrent().equals(org.controlsfx.tools.Platform.UNIX) ? "*" : "*.*"),
                    new FileChooser.ExtensionFilter("CRT", "*.crt")
            );

            File file = fileChooser.showOpenDialog(window);
            if(file != null) {
                electrumCertificate.setText(file.getAbsolutePath());
            }
        });

        useProxy.selectedProperty().addListener((observable, oldValue, newValue) -> {
            config.setUseProxy(newValue);
            proxyHost.setText(proxyHost.getText() + " ");
            proxyHost.setText(proxyHost.getText().trim());
            proxyHost.setDisable(!newValue);
            proxyPort.setDisable(!newValue);

            if(newValue) {
                electrumUseSsl.setSelected(true);
                electrumUseSsl.setDisable(true);
            } else {
                electrumUseSsl.setDisable(false);
            }
        });

        boolean isConnected = ElectrumServer.isConnected();
        setFieldsEditable(!isConnected);

        testConnection.managedProperty().bind(testConnection.visibleProperty());
        testConnection.setVisible(!isConnected);
        setTestResultsFont();
        testConnection.setOnAction(event -> {
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.ELLIPSIS_H, null));
            testResults.setText("Connecting to " + config.getServerAddress() + "...");
            startElectrumConnection();
        });

        editConnection.managedProperty().bind(editConnection.visibleProperty());
        editConnection.setVisible(isConnected);
        editConnection.setOnAction(event -> {
            EventManager.get().post(new RequestDisconnectEvent());
            setFieldsEditable(true);
            editConnection.setVisible(false);
            testConnection.setVisible(true);
        });

        String coreServer = config.getCoreServer();
        if(coreServer != null) {
            Protocol protocol = Protocol.getProtocol(coreServer);

            if(protocol != null) {
                HostAndPort server = protocol.getServerHostAndPort(coreServer);
                coreHost.setText(server.getHost());
                if(server.hasPort()) {
                    corePort.setText(Integer.toString(server.getPort()));
                }
            }
        } else {
            coreHost.setText("127.0.0.1");
            corePort.setText(String.valueOf(Network.get().getDefaultPort()));
        }

        coreDataDir.setText(config.getCoreDataDir() != null ? config.getCoreDataDir().getAbsolutePath() : getDefaultCoreDataDir().getAbsolutePath());

        if(config.getCoreAuth() != null) {
            String[] userPass = config.getCoreAuth().split(":");
            if(userPass.length > 0) {
                coreUser.setText(userPass[0]);
            }
            if(userPass.length > 1) {
                corePass.setText(userPass[1]);
            }
        }

        coreMultiWallet.setSelected(true);
        coreMultiWallet.setSelected(config.getCoreWallet() != null);
        if(config.getCoreWallet() != null) {
            coreWallet.setText(config.getCoreWallet());
        }

        String electrumServer = config.getElectrumServer();
        if(electrumServer != null) {
            Protocol protocol = Protocol.getProtocol(electrumServer);

            if(protocol != null) {
                boolean ssl = protocol.equals(Protocol.SSL);
                electrumUseSsl.setSelected(ssl);
                electrumCertificate.setDisable(!ssl);
                electrumCertificateSelect.setDisable(!ssl);

                HostAndPort server = protocol.getServerHostAndPort(electrumServer);
                electrumHost.setText(server.getHost());
                if(server.hasPort()) {
                    electrumPort.setText(Integer.toString(server.getPort()));
                }
            }
        }

        File certificateFile = config.getElectrumServerCert();
        if(certificateFile != null) {
            electrumCertificate.setText(certificateFile.getAbsolutePath());
        }

        useProxy.setSelected(config.isUseProxy());
        proxyHost.setDisable(!config.isUseProxy());
        proxyPort.setDisable(!config.isUseProxy());

        if(config.isUseProxy()) {
            electrumUseSsl.setSelected(true);
            electrumUseSsl.setDisable(true);
        }

        String proxyServer = config.getProxyServer();
        if(proxyServer != null) {
            HostAndPort server = HostAndPort.fromString(proxyServer);
            proxyHost.setText(server.getHost());
            if(server.hasPort()) {
                proxyPort.setText(Integer.toString(server.getPort()));
            }
        }
    }

    private void startElectrumConnection() {
        if(connectionService != null && connectionService.isRunning()) {
            connectionService.cancel();
        }

        connectionService = new ElectrumServer.ConnectionService(false);
        connectionService.setPeriod(Duration.hours(1));
        EventManager.get().register(connectionService);
        connectionService.statusProperty().addListener((observable, oldValue, newValue) -> {
            testResults.setText(testResults.getText() + "\n" + newValue);
        });

        connectionService.setOnSucceeded(successEvent -> {
            EventManager.get().unregister(connectionService);
            ConnectionEvent connectionEvent = (ConnectionEvent)connectionService.getValue();
            showConnectionSuccess(connectionEvent.getServerVersion(), connectionEvent.getServerBanner());
            connectionService.cancel();
        });
        connectionService.setOnFailed(workerStateEvent -> {
            EventManager.get().unregister(connectionService);
            showConnectionFailure(workerStateEvent);
            connectionService.cancel();
        });
        connectionService.start();
    }

    private void setFieldsEditable(boolean editable) {
        serverTypeToggleGroup.getToggles().forEach(toggle -> ((ToggleButton)toggle).setDisable(!editable));

        coreHost.setEditable(editable);
        corePort.setEditable(editable);
        coreAuthToggleGroup.getToggles().forEach(toggle -> ((ToggleButton)toggle).setDisable(!editable));
        coreDataDir.setEditable(editable);
        coreDataDirSelect.setDisable(!editable);
        coreUser.setEditable(editable);
        corePass.setEditable(editable);
        coreMultiWallet.setDisable(!editable);
        coreWallet.setEditable(editable);

        electrumHost.setEditable(editable);
        electrumPort.setEditable(editable);
        electrumUseSsl.setDisable(!editable);
        electrumCertificate.setEditable(editable);
        electrumCertificateSelect.setDisable(!editable);
        useProxy.setDisable(!editable);
        proxyHost.setEditable(editable);
        proxyPort.setEditable(editable);
    }

    private void showConnectionSuccess(List<String> serverVersion, String serverBanner) {
        testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE, "success"));
        if(serverVersion != null) {
            testResults.setText("Connected to " + serverVersion.get(0) + " on protocol version " + serverVersion.get(1));
            if(ElectrumServer.supportsBatching(serverVersion)) {
                testResults.setText(testResults.getText() + "\nBatched RPC enabled.");
            }
        }
        if(serverBanner != null) {
            testResults.setText(testResults.getText() + "\nServer Banner: " + serverBanner);
        }
    }

    private void showConnectionFailure(WorkerStateEvent failEvent) {
        Throwable e = failEvent.getSource().getException();
        log.error("Connection error", e);
        String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if(e.getCause() != null && e.getCause() instanceof SSLHandshakeException) {
            reason = "SSL Handshake Error\n" + reason;
        }

        testResults.setText("Could not connect:\n\n" + reason);
        testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.EXCLAMATION_CIRCLE, "failure"));
    }

    private void setupValidation() {
        validationSupport.registerValidator(coreHost, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Core host", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(corePort, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Core port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

        validationSupport.registerValidator(coreDataDir, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Core Data Dir required", coreAuthToggleGroup.getSelectedToggle().getUserData() == CoreAuthType.COOKIE && (newValue.isEmpty() || getDirectory(newValue) == null))
        ));

        validationSupport.registerValidator(coreUser, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Core user required", coreAuthToggleGroup.getSelectedToggle().getUserData() == CoreAuthType.USERPASS && newValue.isEmpty())
        ));

        validationSupport.registerValidator(corePass, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Core pass required", coreAuthToggleGroup.getSelectedToggle().getUserData() == CoreAuthType.USERPASS && newValue.isEmpty())
        ));

        validationSupport.registerValidator(coreWallet, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Core wallet required", coreMultiWallet.isSelected() && newValue.isEmpty())
        ));

        validationSupport.registerValidator(electrumHost, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Electrum host", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(electrumPort, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid Electrum port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

        validationSupport.registerValidator(proxyHost, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Proxy host required", useProxy.isSelected() && newValue.isEmpty()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid host name", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(proxyPort, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid proxy port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

        validationSupport.registerValidator(electrumCertificate, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid certificate file", newValue != null && !newValue.isEmpty() && getCertificate(newValue) == null)
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    @NotNull
    private ChangeListener<String> getBitcoinCoreListener(Config config) {
        return (observable, oldValue, newValue) -> {
            setCoreServerInConfig(config);
        };
    }

    private void setCoreServerInConfig(Config config) {
        String hostAsString = getHost(coreHost.getText());
        Integer portAsInteger = getPort(corePort.getText());
        if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
            config.setCoreServer(Protocol.HTTP.toUrlString(hostAsString, portAsInteger));
        } else if(hostAsString != null) {
            config.setCoreServer(Protocol.HTTP.toUrlString(hostAsString));
        }
    }

    @NotNull
    private ChangeListener<String> getBitcoinAuthListener(Config config) {
        return (observable, oldValue, newValue) -> {
            config.setCoreAuth(coreUser.getText() + ":" + corePass.getText());
        };
    }

    @NotNull
    private ChangeListener<String> getBitcoinWalletListener(Config config) {
        return (observable, oldValue, newValue) -> {
            config.setCoreWallet(coreWallet.getText());
        };
    }

    @NotNull
    private ChangeListener<String> getElectrumServerListener(Config config) {
        return (observable, oldValue, newValue) -> {
            setElectrumServerInConfig(config);
        };
    }

    private void setElectrumServerInConfig(Config config) {
        String hostAsString = getHost(electrumHost.getText());
        Integer portAsInteger = getPort(electrumPort.getText());
        if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
            config.setElectrumServer(getProtocol().toUrlString(hostAsString, portAsInteger));
        } else if(hostAsString != null) {
            config.setElectrumServer(getProtocol().toUrlString(hostAsString));
        }
    }

    @NotNull
    private ChangeListener<String> getProxyListener(Config config) {
        return (observable, oldValue, newValue) -> {
            if(oldValue.trim().equals(newValue.trim())) {
                return;
            }

            String hostAsString = getHost(proxyHost.getText());
            Integer portAsInteger = getPort(proxyPort.getText());
            if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
                config.setProxyServer(HostAndPort.fromParts(hostAsString, portAsInteger).toString());
            } else if(hostAsString != null) {
                config.setProxyServer(HostAndPort.fromHost(hostAsString).toString());
            }
        };
    }

    private Protocol getProtocol() {
        return (electrumUseSsl.isSelected() ? Protocol.SSL : Protocol.TCP);
    }

    private String getHost(String text) {
        try {
            return HostAndPort.fromHost(text).getHost();
        } catch(IllegalArgumentException e) {
            return null;
        }
    }

    private Integer getPort(String text) {
        try {
            return Integer.parseInt(text);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    private File getDirectory(String dirLocation) {
        try {
            File dirFile = new File(dirLocation);
            if(!dirFile.exists() || !dirFile.isDirectory()) {
                return null;
            }

            return dirFile;
        } catch (Exception e) {
            return null;
        }
    }

    private File getCertificate(String crtFileLocation) {
        try {
            File crtFile = new File(crtFileLocation);
            if(!crtFile.exists()) {
                return null;
            }

            CertificateFactory.getInstance("X.509").generateCertificate(new FileInputStream(crtFile));
            return crtFile;
        } catch (Exception e) {
            return null;
        }
    }

    private Glyph getGlyph(FontAwesome5.Glyph glyphName, String styleClass) {
        Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
        glyph.setFontSize(12);
        if(styleClass != null) {
            glyph.getStyleClass().add(styleClass);
        }

        return glyph;
    }

    private static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }

    private File getDefaultCoreDataDir() {
        org.controlsfx.tools.Platform platform = org.controlsfx.tools.Platform.getCurrent();
        if(platform == org.controlsfx.tools.Platform.OSX) {
            return new File(System.getProperty("user.home") + "/Library/Application Support/Bitcoin");
        } else if(platform == org.controlsfx.tools.Platform.WINDOWS) {
            return new File(System.getenv("APPDATA") + "/Bitcoin");
        } else {
            return new File(System.getProperty("user.home") + "/.bitcoin");
        }
    }

    private void setTestResultsFont() {
        org.controlsfx.tools.Platform platform = org.controlsfx.tools.Platform.getCurrent();
        if(platform == org.controlsfx.tools.Platform.OSX) {
            testResults.setFont(Font.font("Monaco", 11));
        } else if(platform == org.controlsfx.tools.Platform.WINDOWS) {
            testResults.setFont(Font.font("Lucida Console", 11));
        } else {
            testResults.setFont(Font.font("monospace", 11));
        }
    }

    @Subscribe
    public void bwtStatus(BwtStatusEvent event) {
        if(!(event instanceof BwtSyncStatusEvent)) {
            testResults.appendText("\n" + event.getStatus());
        }
    }

    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        if(connectionService != null && connectionService.isRunning() && event.getProgress() < 100) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            testResults.appendText("\nThe connection to the Bitcoin Core node was successful, but it is still syncing and cannot be used yet.");
            testResults.appendText("\nCurrently " + event.getProgress() + "% completed to date " + dateFormat.format(event.getTip()));
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE, null));
            connectionService.cancel();
        }
    }
}
