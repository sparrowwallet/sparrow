package com.sparrowwallet.sparrow.preferences;

import com.github.arteam.simplejsonrpc.client.exception.JsonRpcException;
import com.google.common.base.Throwables;
import com.google.common.eventbus.Subscribe;
import com.google.common.net.HostAndPort;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.Mode;
import com.sparrowwallet.sparrow.control.ComboBoxTextField;
import com.sparrowwallet.sparrow.control.TextFieldValidator;
import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.net.*;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.control.SegmentedButton;
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

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class ServerPreferencesController extends PreferencesDetailController {
    private static final Logger log = LoggerFactory.getLogger(ServerPreferencesController.class);

    private static final Server MANAGE_ALIASES_SERVER = new Server("tcp://localhost", "Manage Aliases...");

    @FXML
    private ToggleGroup serverTypeToggleGroup;

    @FXML
    private SegmentedButton serverTypeSegmentedButton;

    @FXML
    private ToggleButton publicElectrumToggle;

    @FXML
    private Form publicElectrumForm;

    @FXML
    private ComboBox<PublicElectrumServer> publicElectrumServer;

    @FXML
    private UnlabeledToggleSwitch publicUseProxy;

    @FXML
    private TextField publicProxyHost;

    @FXML
    private TextField publicProxyPort;

    @FXML
    private Form coreForm;

    @FXML
    private ComboBox<Server> recentCoreServers;

    @FXML
    private ComboBoxTextField coreHost;

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
    private UnlabeledToggleSwitch coreUseProxy;

    @FXML
    private TextField coreProxyHost;

    @FXML
    private TextField coreProxyPort;

    @FXML
    private Form electrumForm;

    @FXML
    private ComboBox<Server> recentElectrumServers;

    @FXML
    private ComboBoxTextField electrumHost;

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

    private TorService torService;

    private ElectrumServer.ConnectionService connectionService;

    private Boolean useSslOriginal;

    private Boolean useProxyOriginal;

    @Override
    public void initializeView(Config config) {
        EventManager.get().register(this);
        getMasterController().closingProperty().addListener((observable, oldValue, newValue) -> {
            EventManager.get().unregister(this);
            if(connectionService != null && connectionService.isRunning()) {
                connectionService.cancel();
            }
        });

        Platform.runLater(this::setupValidation);

        publicElectrumForm.managedProperty().bind(publicElectrumForm.visibleProperty());
        coreForm.managedProperty().bind(coreForm.visibleProperty());
        electrumForm.managedProperty().bind(electrumForm.visibleProperty());
        serverTypeToggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if(serverTypeToggleGroup.getSelectedToggle() != null) {
                ServerType existingType = config.getServerType();
                ServerType serverType = (ServerType)newValue.getUserData();
                publicElectrumForm.setVisible(serverType == ServerType.PUBLIC_ELECTRUM_SERVER);
                coreForm.setVisible(serverType == ServerType.BITCOIN_CORE);
                electrumForm.setVisible(serverType == ServerType.ELECTRUM_SERVER);
                config.setServerType(serverType);
                testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE, ""));
                testResults.clear();
                if(existingType != serverType) {
                    EventManager.get().post(new ServerTypeChangedEvent(serverType));
                }
            } else if(oldValue != null) {
                oldValue.setSelected(true);
            }
        });
        ServerType serverType = config.getServerType() != null ?
                (config.getServerType() == ServerType.PUBLIC_ELECTRUM_SERVER && !PublicElectrumServer.supportedNetwork() ? ServerType.BITCOIN_CORE : config.getServerType()) :
                    (config.getCoreServer() == null && config.getElectrumServer() != null ? ServerType.ELECTRUM_SERVER :
                        (config.getCoreServer() != null || !PublicElectrumServer.supportedNetwork() ? ServerType.BITCOIN_CORE : ServerType.PUBLIC_ELECTRUM_SERVER));
        if(!PublicElectrumServer.supportedNetwork()) {
            serverTypeSegmentedButton.getButtons().remove(publicElectrumToggle);
            serverTypeToggleGroup.getToggles().remove(publicElectrumToggle);
        }
        serverTypeToggleGroup.selectToggle(serverTypeToggleGroup.getToggles().stream().filter(toggle -> toggle.getUserData() == serverType).findFirst().orElse(null));

        publicElectrumServer.setItems(FXCollections.observableList(PublicElectrumServer.getServers()));
        publicElectrumServer.getSelectionModel().selectedItemProperty().addListener(getPublicElectrumServerListener(config));

        publicUseProxy.selectedProperty().bindBidirectional(useProxy.selectedProperty());
        publicProxyHost.textProperty().bindBidirectional(proxyHost.textProperty());
        publicProxyPort.textProperty().bindBidirectional(proxyPort.textProperty());

        corePort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        electrumPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        proxyPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        coreProxyPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        publicProxyPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());

        coreHost.textProperty().addListener(getBitcoinCoreListener(config));
        corePort.textProperty().addListener(getBitcoinCoreListener(config));

        coreUser.textProperty().addListener(getBitcoinAuthListener(config));
        corePass.textProperty().addListener(getBitcoinAuthListener(config));

        coreUseProxy.selectedProperty().bindBidirectional(useProxy.selectedProperty());
        coreProxyHost.textProperty().bindBidirectional(proxyHost.textProperty());
        coreProxyPort.textProperty().bindBidirectional(proxyPort.textProperty());

        electrumHost.textProperty().addListener(getElectrumServerListener(config));
        electrumPort.textProperty().addListener(getElectrumServerListener(config));

        proxyHost.textProperty().addListener(getProxyListener(config));
        proxyPort.textProperty().addListener(getProxyListener(config));

        corePort.setPromptText("e.g. " + Network.get().getDefaultPort());
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

        recentCoreServers.setCellFactory(value -> new ServerCell());
        recentCoreServers.setItems(getObservableServerList(Config.get().getRecentCoreServers()));
        recentCoreServers.prefWidthProperty().bind(coreHost.widthProperty());
        recentCoreServers.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                if(newValue == MANAGE_ALIASES_SERVER) {
                    ServerAliasDialog serverAliasDialog = new ServerAliasDialog(ServerType.BITCOIN_CORE);
                    serverAliasDialog.initOwner(recentCoreServers.getScene().getWindow());
                    Optional<Server> optServer = serverAliasDialog.showAndWait();
                    recentCoreServers.setItems(getObservableServerList(Config.get().getRecentCoreServers()));
                    Server selectedServer = optServer.orElseGet(() -> Config.get().getCoreServer());
                    Platform.runLater(() -> recentCoreServers.setValue(selectedServer));
                } else if(newValue.getHostAndPort() != null) {
                    HostAndPort hostAndPort = newValue.getHostAndPort();
                    corePort.setText(hostAndPort.hasPort() ? Integer.toString(hostAndPort.getPort()) : "");
                    if(newValue.getAlias() != null) {
                        coreHost.setText(newValue.getAlias());
                    } else {
                        coreHost.setText(hostAndPort.getHost());
                    }
                    coreHost.positionCaret(coreHost.getText().length());
                }
            }
        });

        recentElectrumServers.setCellFactory(value -> new ServerCell());
        recentElectrumServers.setItems(getObservableServerList(Config.get().getRecentElectrumServers()));
        recentElectrumServers.prefWidthProperty().bind(electrumHost.widthProperty());
        recentElectrumServers.valueProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue != null) {
                if(newValue == MANAGE_ALIASES_SERVER) {
                    ServerAliasDialog serverAliasDialog = new ServerAliasDialog(ServerType.ELECTRUM_SERVER);
                    serverAliasDialog.initOwner(recentElectrumServers.getScene().getWindow());
                    Optional<Server> optServer = serverAliasDialog.showAndWait();
                    recentElectrumServers.setItems(getObservableServerList(Config.get().getRecentElectrumServers()));
                    Server selectedServer = optServer.orElseGet(() -> Config.get().getElectrumServer());
                    Platform.runLater(() -> recentElectrumServers.setValue(selectedServer));
                } else if(newValue.getHostAndPort() != null) {
                    HostAndPort hostAndPort = newValue.getHostAndPort();
                    electrumPort.setText(hostAndPort.hasPort() ? Integer.toString(hostAndPort.getPort()) : "");
                    electrumUseSsl.setSelected(newValue.getProtocol() == Protocol.SSL);
                    if(newValue.getAlias() != null) {
                        electrumHost.setText(newValue.getAlias());
                    } else {
                        electrumHost.setText(hostAndPort.getHost());
                    }
                    electrumHost.positionCaret(electrumHost.getText().length());
                }
            }
        });

        electrumUseSsl.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setElectrumServerInConfig(config);
            electrumCertificate.setDisable(!newValue);
            electrumCertificateSelect.setDisable(!newValue);
            electrumPort.setPromptText(newValue ? "e.g. 50002" : "e.g. 50001");
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

            AppServices.moveToActiveWindowScreen(window, 800, 450);
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
            publicProxyHost.setDisable(!newValue);
            publicProxyPort.setDisable(!newValue);
        });

        boolean isConnected = AppServices.isConnecting() || AppServices.isConnected();

        if(Config.get().getServerType() == ServerType.BITCOIN_CORE && AppServices.isConnecting()) {
            testResults.appendText("Connecting to server, please wait...");
        }

        testConnection.managedProperty().bind(testConnection.visibleProperty());
        testConnection.setVisible(!isConnected);
        setTestResultsFont();
        testConnection.setOnAction(event -> {
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.ELLIPSIS_H, null));
            testResults.setText("Connecting " + (config.hasServer() ? "to " + config.getServer().getUrl() : "") + "...");

            if(Config.get().requiresInternalTor() && Tor.getDefault() == null) {
                startTor();
            } else {
                startElectrumConnection();
            }
        });

        editConnection.managedProperty().bind(editConnection.visibleProperty());
        editConnection.setVisible(isConnected);
        editConnection.setDisable(Config.get().getServerType() == ServerType.BITCOIN_CORE && AppServices.isConnecting());
        editConnection.setOnAction(event -> {
            EventManager.get().post(new RequestDisconnectEvent());
            setFieldsEditable(true);
            editConnection.setVisible(false);
            testConnection.setVisible(true);
        });

        PublicElectrumServer configPublicElectrumServer = PublicElectrumServer.fromServer(config.getPublicElectrumServer());
        if(configPublicElectrumServer == null && PublicElectrumServer.supportedNetwork()) {
            List<PublicElectrumServer> servers = PublicElectrumServer.getServers();
            if(!servers.isEmpty()) {
                publicElectrumServer.setValue(servers.get(new Random().nextInt(servers.size())));
            }
        } else {
            publicElectrumServer.setValue(configPublicElectrumServer);
        }

        Server coreServer = config.getCoreServer();
        if(coreServer != null) {
            HostAndPort hostAndPort = coreServer.getHostAndPort();
            Server server = config.getRecentCoreServers().stream().filter(coreServer::equals).findFirst().orElse(null);
            if(server != null) {
                coreHost.setLeft(getGlyph(FontAwesome5.Glyph.TAG, null));
            }
            coreHost.setText(server == null || server.getAlias() == null ? hostAndPort.getHost() : server.getAlias());
            if(hostAndPort.hasPort()) {
                corePort.setText(Integer.toString(hostAndPort.getPort()));
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

        Server electrumServer = config.getElectrumServer();
        if(electrumServer != null) {
            Protocol protocol = electrumServer.getProtocol();
            boolean ssl = protocol.equals(Protocol.SSL);
            electrumUseSsl.setSelected(ssl);
            electrumCertificate.setDisable(!ssl);
            electrumCertificateSelect.setDisable(!ssl);

            HostAndPort hostAndPort = electrumServer.getHostAndPort();
            Server server = config.getRecentElectrumServers().stream().filter(electrumServer::equals).findFirst().orElse(null);
            if(server != null) {
                electrumHost.setLeft(getGlyph(FontAwesome5.Glyph.TAG, null));
            }
            electrumHost.setText(server == null || server.getAlias() == null ? hostAndPort.getHost() : server.getAlias());
            if(hostAndPort.hasPort()) {
                electrumPort.setText(Integer.toString(hostAndPort.getPort()));
            }
        }

        File certificateFile = config.getElectrumServerCert();
        if(certificateFile != null) {
            electrumCertificate.setText(certificateFile.getAbsolutePath());
        }

        useProxy.setSelected(config.isUseProxy());
        proxyHost.setDisable(!config.isUseProxy());
        proxyPort.setDisable(!config.isUseProxy());
        publicProxyHost.setDisable(!config.isUseProxy());
        publicProxyPort.setDisable(!config.isUseProxy());

        String proxyServer = config.getProxyServer();
        if(proxyServer != null) {
            HostAndPort server = HostAndPort.fromString(proxyServer);
            proxyHost.setText(server.getHost());
            if(server.hasPort()) {
                proxyPort.setText(Integer.toString(server.getPort()));
            }
        }

        setFieldsEditable(!isConnected);
    }

    private void startTor() {
        if(torService != null && torService.isRunning()) {
            return;
        }

        torService = new TorService();
        torService.setPeriod(Duration.hours(1000));
        torService.setRestartOnFailure(false);

        torService.setOnSucceeded(workerStateEvent -> {
            Tor.setDefault(torService.getValue());
            torService.cancel();
            testResults.appendText("\nTor running, connecting to " + Config.get().getServer().getUrl() + "...");
            startElectrumConnection();
        });
        torService.setOnFailed(workerStateEvent -> {
            torService.cancel();
            testResults.appendText("\nTor failed to start");
            showConnectionFailure(workerStateEvent.getSource().getException());
        });

        torService.start();
    }

    private void startElectrumConnection() {
        if(connectionService != null && connectionService.isRunning()) {
            connectionService.cancel();
        }

        connectionService = new ElectrumServer.ConnectionService(false);
        connectionService.setPeriod(Duration.hours(1));
        connectionService.setRestartOnFailure(false);
        EventManager.get().register(connectionService);

        useSslOriginal = null;

        connectionService.setOnSucceeded(successEvent -> {
            EventManager.get().unregister(connectionService);
            ConnectionEvent connectionEvent = (ConnectionEvent)connectionService.getValue();
            showConnectionSuccess(connectionEvent.getServerVersion(), connectionEvent.getServerBanner());
            getMasterController().reconnectOnClosingProperty().set(true);
            Config.get().setMode(Mode.ONLINE);
            connectionService.cancel();
            useProxyOriginal = null;
            if(Config.get().addRecentServer()) {
                if(Config.get().getServerType() == ServerType.BITCOIN_CORE) {
                    recentCoreServers.setItems(getObservableServerList(Config.get().getRecentCoreServers()));
                } else if(Config.get().getServerType() == ServerType.ELECTRUM_SERVER) {
                    recentElectrumServers.setItems(getObservableServerList(Config.get().getRecentElectrumServers()));
                }
            }
        });
        connectionService.setOnFailed(workerStateEvent -> {
            EventManager.get().unregister(connectionService);
            if(connectionService.isShutdown()) {
                connectionService.cancel();
                return;
            }

            showConnectionFailure(workerStateEvent.getSource().getException());
            connectionService.cancel();

            if(Config.get().getServerType() == ServerType.ELECTRUM_SERVER) {
                if(useSslOriginal == null) {
                    Integer portAsInteger = getPort(electrumPort.getText());
                    if(!electrumUseSsl.isSelected() && portAsInteger != null && portAsInteger == Protocol.SSL.getDefaultPort()) {
                        useSslOriginal = false;
                        electrumUseSsl.setSelected(true);
                    } else if(electrumUseSsl.isSelected() && portAsInteger != null && portAsInteger == Protocol.TCP.getDefaultPort()) {
                        useSslOriginal = true;
                        electrumUseSsl.setSelected(false);
                    }

                    if(useSslOriginal != null) {
                        EventManager.get().register(connectionService);
                        connectionService.reset();
                        connectionService.start();
                    }
                } else {
                    electrumUseSsl.setSelected(useSslOriginal);
                    useSslOriginal = null;
                }
            }

            if(useProxyOriginal != null && !useProxyOriginal) {
                useProxy.setSelected(false);
                proxyHost.setText("");
                proxyPort.setText("");
                useProxyOriginal = null;
            }
        });
        connectionService.start();
    }

    private void setFieldsEditable(boolean editable) {
        serverTypeToggleGroup.getToggles().forEach(toggle -> ((ToggleButton)toggle).setDisable(!editable));

        publicElectrumServer.setDisable(!editable);
        publicUseProxy.setDisable(!editable);
        publicProxyHost.setDisable(!editable);
        publicProxyPort.setDisable(!editable);

        coreHost.setDisable(!editable);
        corePort.setDisable(!editable);
        coreAuthToggleGroup.getToggles().forEach(toggle -> ((ToggleButton)toggle).setDisable(!editable));
        coreDataDir.setDisable(!editable);
        coreDataDirSelect.setDisable(!editable);
        coreUser.setDisable(!editable);
        corePass.setDisable(!editable);
        coreUseProxy.setDisable(!editable);
        coreProxyHost.setDisable(!editable);
        coreProxyPort.setDisable(!editable);

        electrumHost.setDisable(!editable);
        electrumPort.setDisable(!editable);
        electrumUseSsl.setDisable(!editable);
        electrumCertificate.setDisable(!editable);
        electrumCertificateSelect.setDisable(!editable);
        useProxy.setDisable(!editable);
        proxyHost.setDisable(!editable);
        proxyPort.setDisable(!editable);
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

    private void showConnectionFailure(Throwable exception) {
        log.error("Connection error", exception);

        String reason;
        if(Throwables.getRootCause(exception) instanceof JsonRpcException jsonRpcException && jsonRpcException.getErrorMessage() != null) {
            reason = jsonRpcException.getErrorMessage().getMessage() + (jsonRpcException.getErrorMessage().getData() != null ? " (" + jsonRpcException.getErrorMessage().getData().asText() + ")" : "");
        } else {
            reason = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        }

        if(exception instanceof TlsServerException && exception.getCause() != null) {
            TlsServerException tlsServerException = (TlsServerException)exception;
            if(exception.getCause().getMessage().contains("PKIX path building failed")) {
                File configCrtFile = Config.get().getElectrumServerCert();
                File savedCrtFile = Storage.getCertificateFile(tlsServerException.getServer().getHost());
                if(configCrtFile == null && savedCrtFile != null) {
                    Optional<ButtonType> optButton = AppServices.showErrorDialog("SSL Handshake Failed", "The certificate provided by the server at " + tlsServerException.getServer().getHost() + " appears to have changed." +
                            "\n\nThis may indicate a man-in-the-middle attack!" +
                            "\n\nDo you still want to proceed?", ButtonType.NO, ButtonType.YES);
                    if(optButton.isPresent() && optButton.get() == ButtonType.YES) {
                        if(savedCrtFile.delete()) {
                            Platform.runLater(this::startElectrumConnection);
                            return;
                        } else {
                            AppServices.showErrorDialog("Could not delete certificate", "The certificate file at " + savedCrtFile.getAbsolutePath() + " could not be deleted.\n\nPlease delete this file manually.");
                        }
                    }
                }
            }

            reason = tlsServerException.getMessage() + "\n\n" + reason;
        } else if(exception instanceof ProxyServerException) {
            reason += ". Check if the proxy server is running.";
        } else if(reason != null && (reason.contains("Check if Bitcoin Core is running") || reason.contains("Could not connect to Bitcoin Core RPC"))) {
            reason += "\n\nSee https://sparrowwallet.com/docs/connect-node.html";
        } else if(reason != null && (reason.startsWith("Cannot connect to hidden service"))) {
            reason += " on the server. Check that the onion address and port are correct, and that both Tor and the Electrum server are running on the node. Usually SSL is not enabled, and the port is 50001.";
        } else if(reason != null && (reason.startsWith("Cannot find Bitcoin Core cookie file at"))) {
            reason += "\n\nMake sure server=1 has been added to bitcoin.conf";
        }

        testResults.setText("Could not connect:\n\n" + reason);
        testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.EXCLAMATION_CIRCLE, "failure"));
    }

    private void setupValidation() {
        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());

        validationSupport.registerValidator(publicProxyHost, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Proxy host required", publicUseProxy.isSelected() && newValue.isEmpty()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid host name", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(publicProxyPort, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid proxy port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

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
    }

    @NotNull
    private ChangeListener<PublicElectrumServer> getPublicElectrumServerListener(Config config) {
        return (observable, oldValue, newValue) -> {
            config.setPublicElectrumServer(newValue.getServer());
        };
    }

    @NotNull
    private ChangeListener<String> getBitcoinCoreListener(Config config) {
        return (observable, oldValue, newValue) -> {
            Server existingServer = config.getRecentCoreServers().stream().filter(server -> coreHost.getText().equals(server.getAlias())).findFirst().orElse(null);
            if(existingServer != null && !existingServer.portEquals(corePort.getText())) {
                coreHost.setText(existingServer.getHost());
                existingServer = null;
            }
            coreHost.setLeft(existingServer == null ? null : getGlyph(FontAwesome5.Glyph.TAG, null));
            setCoreServerInConfig(config);
        };
    }

    private void setCoreServerInConfig(Config config) {
        Server existingServer = config.getRecentCoreServers().stream().filter(server -> coreHost.getText().equals(server.getAlias())).findFirst().orElse(null);
        if(existingServer != null) {
            config.setCoreServer(existingServer);
            return;
        }

        String hostAsString = getHost(coreHost.getText());
        Integer portAsInteger = getPort(corePort.getText());
        if(hostAsString != null && !hostAsString.isEmpty() && portAsInteger != null && isValidPort(portAsInteger)) {
            Protocol protocol = portAsInteger == Protocol.HTTPS.getDefaultPort() ? Protocol.HTTPS : Protocol.HTTP;
            config.setCoreServer(new Server(protocol.toUrlString(hostAsString, portAsInteger)));
        } else if(hostAsString != null && !hostAsString.isEmpty()) {
            config.setCoreServer(new Server(Protocol.HTTP.toUrlString(hostAsString)));
        } else {
            config.setCoreServer(null);
        }
    }

    @NotNull
    private ChangeListener<String> getBitcoinAuthListener(Config config) {
        return (observable, oldValue, newValue) -> {
            config.setCoreAuth(coreUser.getText() + ":" + corePass.getText());
        };
    }

    @NotNull
    private ChangeListener<String> getElectrumServerListener(Config config) {
        return (observable, oldValue, newValue) -> {
            Server existingServer = config.getRecentElectrumServers().stream().filter(server -> electrumHost.getText().equals(server.getAlias())).findFirst().orElse(null);
            if(existingServer != null && !existingServer.portEquals(electrumPort.getText())) {
                electrumHost.setText(existingServer.getHost());
                existingServer = null;
            }
            electrumHost.setLeft(existingServer == null ? null : getGlyph(FontAwesome5.Glyph.TAG, null));
            setElectrumServerInConfig(config);
        };
    }

    private void setElectrumServerInConfig(Config config) {
        Server existingServer = config.getRecentElectrumServers().stream().filter(server -> electrumHost.getText().equals(server.getAlias())).findFirst().orElse(null);
        if(existingServer != null) {
            config.setElectrumServer(existingServer);
            return;
        }

        String hostAsString = getHost(electrumHost.getText());
        Integer portAsInteger = getPort(electrumPort.getText());
        if(hostAsString != null && !hostAsString.isEmpty() && portAsInteger != null && isValidPort(portAsInteger)) {
            config.setElectrumServer(new Server(getProtocol().toUrlString(hostAsString, portAsInteger)));
        } else if(hostAsString != null && !hostAsString.isEmpty()) {
            config.setElectrumServer(new Server(getProtocol().toUrlString(hostAsString)));
        } else {
            config.setElectrumServer(null);
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

    private static Glyph getGlyph(FontAwesome5.Glyph glyphName, String styleClass) {
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

    private ObservableList<Server> getObservableServerList(List<Server> servers) {
        ObservableList<Server> serverObservableList = FXCollections.observableList(new ArrayList<>(servers));
        serverObservableList.add(MANAGE_ALIASES_SERVER);
        return serverObservableList;
    }

    @Subscribe
    public void cormorantSyncStatus(CormorantSyncStatusEvent event) {
        editConnection.setDisable(false);
        if(connectionService != null && connectionService.isRunning() && event.getProgress() < 100) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            testResults.appendText("\nThe connection to the Bitcoin Core node was successful, but it is still syncing and cannot be used yet.");
            testResults.appendText("\nCurrently " + event.getProgress() + "% completed to date " + dateFormat.format(event.getTip()));
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE, null));
            connectionService.cancel();
        }
    }

    @Subscribe
    public void bwtStatus(BwtStatusEvent event) {
        if(!(event instanceof BwtSyncStatusEvent)) {
            testResults.appendText("\n" + event.getStatus());
        }
        if(event instanceof BwtReadyStatusEvent) {
            editConnection.setDisable(false);
        }
    }

    @Subscribe
    public void bwtSyncStatus(BwtSyncStatusEvent event) {
        editConnection.setDisable(false);
        if(connectionService != null && connectionService.isRunning() && event.getProgress() < 100) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
            testResults.appendText("\nThe connection to the Bitcoin Core node was successful, but it is still syncing and cannot be used yet.");
            testResults.appendText("\nCurrently " + event.getProgress() + "% completed to date " + dateFormat.format(event.getTip()));
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.QUESTION_CIRCLE, null));
            connectionService.cancel();
        }
    }

    @Subscribe
    public void torStatus(TorStatusEvent event) {
        Platform.runLater(() -> {
            if(torService != null && torService.isRunning()) {
                testResults.appendText("\n" + event.getStatus());
            }
        });
    }

    private static class ServerCell extends ListCell<Server> {
        @Override
        protected void updateItem(Server server, boolean empty) {
            super.updateItem(server, empty);
            if(server == null || empty) {
                setText("");
                setGraphic(null);
            } else {
                String serverAlias = server.getAlias();

                if(server == MANAGE_ALIASES_SERVER) {
                    setText(serverAlias);
                    setStyle("-fx-font-style: italic");
                    setGraphic(null);
                } else if(serverAlias != null) {
                    setText(serverAlias);
                    setGraphic(getGlyph(FontAwesome5.Glyph.TAG, null));
                } else {
                    setText(server.getHost());
                    setGraphic(null);
                }
            }
        }
    }
}
