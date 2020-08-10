package com.sparrowwallet.sparrow.preferences;

import com.google.common.net.HostAndPort;
import com.sparrowwallet.sparrow.control.TextFieldValidator;
import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.event.ConnectionEvent;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.net.Protocol;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.WorkerStateEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.controlsfx.glyphfont.Glyph;
import org.controlsfx.validation.ValidationResult;
import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;
import org.controlsfx.validation.decoration.StyleClassValidationDecoration;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.util.List;

public class ServerPreferencesController extends PreferencesDetailController {
    @FXML
    private TextField host;

    @FXML
    private TextField port;

    @FXML
    private UnlabeledToggleSwitch useSsl;

    @FXML
    private TextField certificate;

    @FXML
    private Button certificateSelect;

    @FXML
    private UnlabeledToggleSwitch useProxy;

    @FXML
    private TextField proxyHost;

    @FXML
    private TextField proxyPort;

    @FXML
    private Button testConnection;

    @FXML
    private TextArea testResults;

    private final ValidationSupport validationSupport = new ValidationSupport();

    @Override
    public void initializeView(Config config) {
        Platform.runLater(this::setupValidation);

        port.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());
        proxyPort.setTextFormatter(new TextFieldValidator(TextFieldValidator.ValidationModus.MAX_INTEGERS, 5).getFormatter());

        host.textProperty().addListener(getElectrumServerListener(config));
        port.textProperty().addListener(getElectrumServerListener(config));

        proxyHost.textProperty().addListener(getProxyListener(config));
        proxyPort.textProperty().addListener(getProxyListener(config));

        useSsl.selectedProperty().addListener((observable, oldValue, newValue) -> {
            setElectrumServerInConfig(config);
            certificate.setDisable(!newValue);
            certificateSelect.setDisable(!newValue);
        });

        certificate.textProperty().addListener((observable, oldValue, newValue) -> {
            File crtFile = getCertificate(newValue);
            if(crtFile != null) {
                config.setElectrumServerCert(crtFile);
            } else {
                config.setElectrumServerCert(null);
            }
        });

        certificateSelect.setOnAction(event -> {
            Stage window = new Stage();

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Electrum Server certificate");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("All Files", "*.*"),
                    new FileChooser.ExtensionFilter("CRT", "*.crt")
            );

            File file = fileChooser.showOpenDialog(window);
            if(file != null) {
                certificate.setText(file.getAbsolutePath());
            }
        });

        useProxy.selectedProperty().addListener((observable, oldValue, newValue) -> {
            config.setUseProxy(newValue);
            proxyHost.setText(proxyHost.getText() + " ");
            proxyHost.setText(proxyHost.getText().trim());
            proxyHost.setDisable(!newValue);
            proxyPort.setDisable(!newValue);

            if(newValue) {
                useSsl.setSelected(true);
                useSsl.setDisable(true);
            } else {
                useSsl.setDisable(false);
            }
        });

        testConnection.setOnAction(event -> {
            testResults.setText("Connecting to " + config.getElectrumServer() + "...");
            testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.ELLIPSIS_H, null));

            boolean existingConnection = ElectrumServer.isConnected();
            if(existingConnection) {
                ElectrumServer.ServerBannerService serverBannerService = new ElectrumServer.ServerBannerService();
                serverBannerService.setOnSucceeded(successEvent -> {
                    showConnectionSuccess(null, serverBannerService.getValue());
                });
                serverBannerService.setOnFailed(this::showConnectionFailure);
                serverBannerService.start();
            } else {
                ElectrumServer.ConnectionService connectionService = new ElectrumServer.ConnectionService(false);
                connectionService.setPeriod(Duration.minutes(1));
                connectionService.setOnSucceeded(successEvent -> {
                    ConnectionEvent connectionEvent = (ConnectionEvent)connectionService.getValue();
                    showConnectionSuccess(connectionEvent.getServerVersion(), connectionEvent.getServerBanner());
                    connectionService.cancel();
                });
                connectionService.setOnFailed(workerStateEvent -> {
                    showConnectionFailure(workerStateEvent);
                    connectionService.cancel();
                });
                connectionService.start();
            }
        });

        String electrumServer = config.getElectrumServer();
        if(electrumServer != null) {
            Protocol protocol = Protocol.getProtocol(electrumServer);

            if(protocol != null) {
                boolean ssl = protocol.equals(Protocol.SSL);
                useSsl.setSelected(ssl);
                certificate.setDisable(!ssl);
                certificateSelect.setDisable(!ssl);

                HostAndPort server = protocol.getServerHostAndPort(electrumServer);
                host.setText(server.getHost());
                if(server.hasPort()) {
                    port.setText(Integer.toString(server.getPort()));
                }
            }
        }

        File certificateFile = config.getElectrumServerCert();
        if(certificateFile != null) {
            certificate.setText(certificateFile.getAbsolutePath());
        }

        useProxy.setSelected(config.isUseProxy());
        proxyHost.setDisable(!config.isUseProxy());
        proxyPort.setDisable(!config.isUseProxy());

        if(config.isUseProxy()) {
            useSsl.setSelected(true);
            useSsl.setDisable(true);
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

    private void showConnectionSuccess(List<String> serverVersion, String serverBanner) {
        testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.CHECK_CIRCLE, Color.rgb(80, 161, 79)));
        if(serverVersion != null) {
            testResults.setText("Connected to " + serverVersion.get(0) + " on protocol version " + serverVersion.get(1));
        }
        if(serverBanner != null) {
            testResults.setText(testResults.getText() + "\nServer Banner: " + serverBanner);
        }
    }

    private void showConnectionFailure(WorkerStateEvent failEvent) {
        Throwable e = failEvent.getSource().getException();
        String reason = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if(e.getCause() != null && e.getCause() instanceof SSLHandshakeException) {
            reason = "SSL Handshake Error\n" + reason;
        }

        testResults.setText("Could not connect:\n\n" + reason);
        testConnection.setGraphic(getGlyph(FontAwesome5.Glyph.EXCLAMATION_CIRCLE, Color.rgb(202, 18, 67)));
    }

    private void setupValidation() {
        validationSupport.registerValidator(host, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid host name", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(port, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

        validationSupport.registerValidator(proxyHost, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Proxy host required", useProxy.isSelected() && newValue.isEmpty()),
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid host name", getHost(newValue) == null)
        ));

        validationSupport.registerValidator(proxyPort, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid proxy port", !newValue.isEmpty() && !isValidPort(Integer.parseInt(newValue)))
        ));

        validationSupport.registerValidator(certificate, Validator.combine(
                (Control c, String newValue) -> ValidationResult.fromErrorIf( c, "Invalid certificate file", newValue != null && !newValue.isEmpty() && getCertificate(newValue) == null)
        ));

        validationSupport.setValidationDecorator(new StyleClassValidationDecoration());
    }

    @NotNull
    private ChangeListener<String> getElectrumServerListener(Config config) {
        return (observable, oldValue, newValue) -> {
            setElectrumServerInConfig(config);
        };
    }

    private void setElectrumServerInConfig(Config config) {
        String hostAsString = getHost(host.getText());
        Integer portAsInteger = getPort(port.getText());
        if(hostAsString != null && portAsInteger != null && isValidPort(portAsInteger)) {
            config.setElectrumServer(getProtocol().toUrlString(hostAsString, portAsInteger));
        } else if(hostAsString != null) {
            config.setElectrumServer(getProtocol().toUrlString(hostAsString));
        }
    }

    @NotNull
    private ChangeListener<String> getProxyListener(Config config) {
        return (observable, oldValue, newValue) -> {
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
        return (useSsl.isSelected() ? Protocol.SSL : Protocol.TCP);
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

    private Glyph getGlyph(FontAwesome5.Glyph glyphName, Color color) {
        Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, glyphName);
        glyph.setFontSize(13);
        if(color != null) {
            glyph.setColor(color);
        }

        return glyph;
    }

    private static boolean isValidPort(int port) {
        return port >= 0 && port <= 65535;
    }
}
