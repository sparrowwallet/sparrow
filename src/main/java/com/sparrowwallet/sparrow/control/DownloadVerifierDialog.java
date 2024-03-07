package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.pgp.PGPKeySource;
import com.sparrowwallet.drongo.pgp.PGPUtils;
import com.sparrowwallet.drongo.pgp.PGPVerificationException;
import com.sparrowwallet.drongo.pgp.PGPVerificationResult;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.GlyphUtils;
import com.sparrowwallet.sparrow.net.VersionCheckService;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.tools.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tornadofx.control.Field;
import tornadofx.control.Fieldset;
import tornadofx.control.Form;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppController.DRAG_OVER_CLASS;

public class DownloadVerifierDialog extends Dialog<ButtonBar.ButtonData> {
    private static final Logger log = LoggerFactory.getLogger(DownloadVerifierDialog.class);

    private static final DateFormat signatureDateFormat = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy z");

    private static final long MAX_VALID_MANIFEST_SIZE = 100 * 1024;
    private static final String SHA256SUMS_MANIFEST_PREFIX = "sha256sums";

    private static final List<String> SIGNATURE_EXTENSIONS = List.of("asc", "sig", "gpg");
    private static final List<String> MANIFEST_EXTENSIONS = List.of("txt");
    private static final List<String> PUBLIC_KEY_EXTENSIONS = List.of("asc");
    private static final List<String> MACOS_RELEASE_EXTENSIONS = List.of("dmg");
    private static final List<String> WINDOWS_RELEASE_EXTENSIONS = List.of("exe", "zip");
    private static final List<String> LINUX_RELEASE_EXTENSIONS = List.of("deb", "rpm", "tar.gz");
    private static final List<String> DISK_IMAGE_EXTENSIONS = List.of("img", "bin", "dfu");
    private static final List<String> ARCHIVE_EXTENSIONS = List.of("zip", "tar.gz", "tar.bz2", "tar.xz", "rar", "7z");

    private static final String SPARROW_RELEASE_PREFIX = "sparrow-";
    private static final String SPARROW_SIGNATURE_SUFFIX = "-manifest.txt.asc";
    private static final Pattern SPARROW_RELEASE_VERSION = Pattern.compile("[0-9]+(\\.[0-9]+)*");
    private static final long MIN_VALID_SPARROW_RELEASE_SIZE = 10 * 1024 * 1024;

    private final ObjectProperty<File> signature = new SimpleObjectProperty<>();
    private final ObjectProperty<File> manifest = new SimpleObjectProperty<>();
    private final ObjectProperty<File> publicKey = new SimpleObjectProperty<>();
    private final ObjectProperty<File> release = new SimpleObjectProperty<>();

    private final BooleanProperty publicKeyDisabled = new SimpleBooleanProperty();

    private final Label signedBy;
    private final Label releaseHash;
    private final Label releaseVerified;
    private final Hyperlink releaseLink;

    private static File lastFileParent;

    public DownloadVerifierDialog(File initialSignatureFile) {
        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.getStylesheets().add(AppServices.class.getResource("dialog.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        dialogPane.setHeader(new Header());
        setupDrag(dialogPane);

        VBox vBox = new VBox();
        vBox.setSpacing(20);
        vBox.setPadding(new Insets(20, 10, 10, 20));

        Form form = new Form();
        Fieldset filesFieldset = new Fieldset();
        filesFieldset.setText("Files");
        filesFieldset.setSpacing(10);

        String version = VersionCheckService.getVersion() != null ? VersionCheckService.getVersion() : "x.x.x";

        Field signatureField = setupField(signature, "Signature", SIGNATURE_EXTENSIONS, false, "sparrow-" + version + "-manifest.txt", null);
        Field manifestField = setupField(manifest, "Manifest", MANIFEST_EXTENSIONS, false, "sparrow-" + version + "-manifest", null);
        Field publicKeyField = setupField(publicKey, "Public Key", PUBLIC_KEY_EXTENSIONS, true, "pgp_keys", publicKeyDisabled);
        Field releaseFileField = setupField(release, "Release File", getReleaseFileExtensions(), false, getReleaseFileExample(version), null);

        filesFieldset.getChildren().addAll(signatureField, manifestField, publicKeyField, releaseFileField);
        form.getChildren().add(filesFieldset);

        Fieldset resultsFieldset = new Fieldset();
        resultsFieldset.setText("Results");
        resultsFieldset.setSpacing(10);

        signedBy = new Label();
        Field signedByField = setupResultField(signedBy, "Signed By");

        releaseHash = new Label();
        Field hashMatchedField = setupResultField(releaseHash, "Release Hash");

        releaseVerified = new Label();
        Field releaseVerifiedField = setupResultField(releaseVerified, "Verified");

        releaseLink = new Hyperlink("");
        releaseVerifiedField.getInputs().add(releaseLink);
        releaseLink.setOnAction(event -> {
            if(release.get() != null && release.get().exists()) {
                if(release.get().getName().toLowerCase(Locale.ROOT).startsWith("sparrow")) {
                    Optional<ButtonType> optType = AppServices.showAlertDialog("Exit Sparrow?", "Sparrow must be closed before installation. Exit?", Alert.AlertType.CONFIRMATION, ButtonType.NO, ButtonType.YES);
                    if(optType.isPresent() && optType.get() == ButtonType.YES) {
                        javafx.application.Platform.exit();
                        AppServices.get().getApplication().getHostServices().showDocument("file://" + release.get().getAbsolutePath());
                    }
                } else {
                    AppServices.get().getApplication().getHostServices().showDocument("file://" + release.get().getAbsolutePath());
                }
            }
        });

        resultsFieldset.getChildren().addAll(signedByField, hashMatchedField, releaseVerifiedField);
        form.getChildren().add(resultsFieldset);

        vBox.getChildren().addAll(form);
        dialogPane.setContent(vBox);

        ButtonType clearButtonType = new javafx.scene.control.ButtonType("Clear", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType closeButtonType = new javafx.scene.control.ButtonType("Close", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(clearButtonType, closeButtonType);

        setOnCloseRequest(event -> {
            if(ButtonBar.ButtonData.CANCEL_CLOSE.equals(getResult())) {
                signature.set(null);
                manifest.set(null);
                publicKey.set(null);
                release.set(null);
                signedBy.setText("");
                signedBy.setGraphic(null);
                signedBy.setTooltip(null);
                releaseHash.setText("");
                releaseHash.setGraphic(null);
                releaseVerified.setText("");
                releaseVerified.setGraphic(null);
                releaseLink.setText("");
                event.consume();
            }
        });
        setResultConverter(ButtonType::getButtonData);

        AppServices.moveToActiveWindowScreen(this);
        dialogPane.setPrefWidth(900);
        setResizable(true);

        signature.addListener((observable, oldValue, signatureFile) -> {
            if(signatureFile != null) {
                boolean verify = true;
                File actualSignatureFile = findSignatureFile(signatureFile);
                if(actualSignatureFile != null  && !actualSignatureFile.equals(signature.get())) {
                    signature.set(actualSignatureFile);
                    verify = false;
                } else if(PGPUtils.signatureContainsManifest(signatureFile)) {
                    manifest.set(signatureFile);
                    verify = false;
                } else {
                    File manifestFile = findManifestFile(signatureFile);
                    if(manifestFile != null && !manifestFile.equals(manifest.get())) {
                        manifest.set(manifestFile);
                        verify = false;
                    }
                }

                if(verify) {
                    verify();
                }
            }
        });

        manifest.addListener((observable, oldValue, manifestFile) -> {
            if(manifestFile != null) {
                boolean verify = true;
                try {
                    Map<File, String> manifestMap = getManifest(manifestFile);
                    File releaseFile = findReleaseFile(manifestFile, manifestMap);
                    if(releaseFile != null && !releaseFile.equals(release.get())) {
                        release.set(releaseFile);
                        verify = false;
                    }
                } catch(IOException e) {
                    log.debug("Error reading manifest file", e);
                    verify = false;
                } catch(InvalidManifestException e) {
                    release.set(manifestFile);
                    verify = false;
                }

                if(verify) {
                    verify();
                }
            }
        });

        publicKey.addListener((observable, oldValue, newValue) -> {
            verify();
        });

        release.addListener((observable, oldValue, releaseFile) -> {
            verify();
        });

        if(initialSignatureFile != null) {
            javafx.application.Platform.runLater(() -> signature.set(initialSignatureFile));
        }
    }

    private void setupDrag(DialogPane dialogPane) {
        dialogPane.setOnDragOver(event -> {
            if(event.getGestureSource() != dialogPane && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.LINK);
            }
            event.consume();
        });

        dialogPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if(db.hasFiles()) {
                for(File file : db.getFiles()) {
                    if(isVerifyDownloadFile(file)) {
                        signature.set(file);
                        break;
                    }
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        dialogPane.setOnDragEntered(event -> {
            dialogPane.getStyleClass().add(DRAG_OVER_CLASS);
        });

        dialogPane.setOnDragExited(event -> {
            dialogPane.getStyleClass().removeAll(DRAG_OVER_CLASS);
        });
    }

    private void verify() {
        publicKeyDisabled.set(false);

        if(signature.get() == null || manifest.get() == null) {
            clearReleaseFields();
            return;
        }

        PGPVerifyService pgpVerifyService = new PGPVerifyService(signature.get(), manifest.get(), publicKey.get());
        pgpVerifyService.setOnRunning(event -> {
            signedBy.setText("Verifying...");
            signedBy.setGraphic(GlyphUtils.getBusyGlyph());
            signedBy.setTooltip(null);
            clearReleaseFields();
        });
        pgpVerifyService.setOnSucceeded(event -> {
            PGPVerificationResult result = pgpVerifyService.getValue();

            String message = result.userId() + " on " + signatureDateFormat.format(result.signatureTimestamp()) + (result.expired() ? " (key expired)" : "");
            signedBy.setText(message);
            signedBy.setGraphic(result.expired() ? GlyphUtils.getWarningGlyph() : GlyphUtils.getSuccessGlyph());
            signedBy.setTooltip(new Tooltip(result.fingerprint()));

            if(!result.expired() && result.keySource() != PGPKeySource.USER) {
                publicKeyDisabled.set(true);
            }

            if(manifest.get().equals(release.get())) {
                releaseHash.setText("No hash required, signature signs release file directly");
                releaseHash.setGraphic(GlyphUtils.getSuccessGlyph());
                releaseHash.setTooltip(null);
                releaseVerified.setText("Ready to install ");
                releaseVerified.setGraphic(GlyphUtils.getSuccessGlyph());
                releaseLink.setText(release.get().getName());
            } else {
                verifyManifest();
            }
        });
        pgpVerifyService.setOnFailed(event -> {
            Throwable e = event.getSource().getException();
            signedBy.setText(getDisplayMessage(e));
            signedBy.setGraphic(GlyphUtils.getFailureGlyph());
            signedBy.setTooltip(null);
            clearReleaseFields();
        });

        pgpVerifyService.start();
    }

    private void clearReleaseFields() {
        releaseHash.setText("");
        releaseHash.setGraphic(null);
        releaseHash.setTooltip(null);
        releaseVerified.setText("");
        releaseVerified.setGraphic(null);
        releaseLink.setText("");
    }

    private void verifyManifest() {
        File releaseFile = release.get();
        if(releaseFile != null && releaseFile.exists()) {
            FileSha256Service hashService = new FileSha256Service(releaseFile);
            hashService.setOnRunning(event -> {
                releaseHash.setText("Calculating...");
                releaseHash.setGraphic(GlyphUtils.getBusyGlyph());
                releaseHash.setTooltip(null);
                releaseVerified.setText("");
                releaseVerified.setGraphic(null);
                releaseLink.setText("");
            });
            hashService.setOnSucceeded(event -> {
                String calculatedHash = hashService.getValue();
                try {
                    Map<File, String> manifestMap = getManifest(manifest.get());
                    String manifestHash = getManifestHash(releaseFile.getName(), manifestMap);
                    if(calculatedHash.equalsIgnoreCase(manifestHash)) {
                        releaseHash.setText("Matched manifest hash");
                        releaseHash.setGraphic(GlyphUtils.getSuccessGlyph());
                        releaseHash.setTooltip(new Tooltip(calculatedHash));
                        releaseVerified.setText("Ready to install ");
                        releaseVerified.setGraphic(GlyphUtils.getSuccessGlyph());
                        releaseLink.setText(releaseFile.getName());
                    } else if(manifestHash == null) {
                        releaseHash.setText("Could not find manifest hash for " + releaseFile.getName());
                        releaseHash.setGraphic(GlyphUtils.getFailureGlyph());
                        releaseHash.setTooltip(new Tooltip("Manifest hashes provided for:\n" + manifestMap.keySet().stream().map(File::getName).collect(Collectors.joining("\n"))));
                        releaseVerified.setText("Cannot verify " + releaseFile.getName());
                        releaseVerified.setGraphic(GlyphUtils.getFailureGlyph());
                        releaseLink.setText("");
                    } else {
                        releaseHash.setText("Did not match manifest hash");
                        releaseHash.setGraphic(GlyphUtils.getFailureGlyph());
                        releaseHash.setTooltip(new Tooltip("Calculated Hash: " + calculatedHash + "\nManifest Hash: " + manifestHash));
                        releaseVerified.setText("Cannot verify " + releaseFile.getName());
                        releaseVerified.setGraphic(GlyphUtils.getFailureGlyph());
                        releaseLink.setText("");
                    }
                } catch(IOException | InvalidManifestException e) {
                    releaseHash.setText("Could not read manifest");
                    releaseHash.setGraphic(GlyphUtils.getFailureGlyph());
                    releaseHash.setTooltip(new Tooltip(e.getMessage()));
                    releaseVerified.setText("Cannot verify " + releaseFile.getName());
                    releaseVerified.setGraphic(GlyphUtils.getFailureGlyph());
                    releaseLink.setText("");
                }
            });
            hashService.setOnFailed(event -> {
                releaseHash.setText("Could not calculate manifest");
                releaseHash.setGraphic(GlyphUtils.getFailureGlyph());
                releaseHash.setTooltip(new Tooltip(event.getSource().getException().getMessage()));
                releaseVerified.setText("Cannot verify " + releaseFile.getName());
                releaseVerified.setGraphic(GlyphUtils.getFailureGlyph());
                releaseLink.setText("");
            });
            hashService.start();
        } else {
            releaseHash.setText("No release file");
            releaseHash.setGraphic(GlyphUtils.getFailureGlyph());
            releaseHash.setTooltip(null);
            releaseVerified.setText("Not verified");
            releaseVerified.setGraphic(GlyphUtils.getFailureGlyph());
            releaseLink.setText("");
        }
    }

    private Field setupField(ObjectProperty<File> fileProperty, String title, List<String> extensions, boolean optional, String example, BooleanProperty disabledProperty) {
        Field field = new Field();
        field.setText(title + ":");
        FileField fileField = new FileField(fileProperty, title, extensions, optional, example, disabledProperty);
        field.getInputs().add(fileField);
        return field;
    }

    private Field setupResultField(Label label, String title) {
        Field field = new Field();
        field.setText(title + ":");
        field.getInputs().add(label);
        label.setGraphicTextGap(8);
        return field;
    }

    public static Map<File, String> getManifest(File manifest) throws IOException, InvalidManifestException {
        if(manifest.length() > MAX_VALID_MANIFEST_SIZE) {
            throw new InvalidManifestException();
        }

        try(InputStream manifestStream = new FileInputStream(manifest)) {
            return getManifest(manifestStream);
        }
    }

    public static Map<File, String> getManifest(InputStream manifestStream) throws IOException {
        Map<File, String> manifest = new HashMap<>();

        BufferedReader reader = new BufferedReader(new InputStreamReader(manifestStream, StandardCharsets.UTF_8));
        String line;
        while((line = reader.readLine()) != null) {
            String[] parts = line.split("\\s+");
            if(parts.length > 1 && parts[0].length() == 64) {
                String manifestHash = parts[0];
                String manifestFileName = parts[1];
                if(manifestFileName.startsWith("*") || manifestFileName.startsWith("U") || manifestFileName.startsWith("^")) {
                    manifestFileName = manifestFileName.substring(1);
                }
                manifest.put(new File(manifestFileName), manifestHash);
            }
        }

        return manifest;
    }

    private String getManifestHash(String contentFileName, Map<File, String> manifest) {
        for(Map.Entry<File, String> entry : manifest.entrySet()) {
            if(contentFileName.equalsIgnoreCase(entry.getKey().getName())) {
                return entry.getValue();
            }
        }

        return null;
    }

    private File findSignatureFile(File providedFile) {
        for(String extension : SIGNATURE_EXTENSIONS) {
            File signatureFile = new File(providedFile.getParentFile(), providedFile.getName() + "." + extension);
            if(signatureFile.exists()) {
                return signatureFile;
            }
        }

        if(providedFile.getName().toLowerCase(Locale.ROOT).startsWith(SPARROW_RELEASE_PREFIX)) {
            Matcher matcher = SPARROW_RELEASE_VERSION.matcher(providedFile.getName());
            if(matcher.find()) {
                String version = matcher.group();
                File signatureFile = new File(providedFile.getParentFile(), SPARROW_RELEASE_PREFIX + version + SPARROW_SIGNATURE_SUFFIX);
                if(signatureFile.exists()) {
                    return signatureFile;
                }
            }
        }

        return null;
    }

    private File findManifestFile(File providedFile) {
        String signatureName = providedFile.getName();
        if(signatureName.length() > 4 && SIGNATURE_EXTENSIONS.stream().anyMatch(ext -> signatureName.toLowerCase(Locale.ROOT).endsWith("." + ext))) {
            File manifestFile = new File(providedFile.getParent(), signatureName.substring(0, signatureName.length() - 4));
            if(manifestFile.exists()) {
                return manifestFile;
            }
        }

        return null;
    }

    private File findReleaseFile(File manifestFile, Map<File, String> manifestMap) {
        List<String> releaseExtensions = getReleaseFileExtensions();
        List<List<String>> extensionLists = List.of(releaseExtensions, DISK_IMAGE_EXTENSIONS, ARCHIVE_EXTENSIONS, List.of(""));

        for(List<String> extensions : extensionLists) {
            for(File file : manifestMap.keySet()) {
                if(extensions.stream().anyMatch(ext -> file.getName().toLowerCase(Locale.ROOT).endsWith(ext))) {
                    File releaseFile = new File(manifestFile.getParent(), file.getName());
                    if(releaseFile.exists()) {
                        return releaseFile;
                    }
                }
            }
        }

        return null;
    }

    private List<String> getReleaseFileExtensions() {
        Platform platform = Platform.getCurrent();
        switch(platform) {
            case OSX -> {
                return MACOS_RELEASE_EXTENSIONS;
            }
            case WINDOWS -> {
                return WINDOWS_RELEASE_EXTENSIONS;
            }
            default -> {
                return LINUX_RELEASE_EXTENSIONS;
            }
        }
    }

    private String getReleaseFileExample(String version) {
        Platform platform = Platform.getCurrent();
        String arch = System.getProperty("os.arch");
        switch(platform) {
            case OSX -> {
                return "Sparrow-" + version + "-" + arch;
            }
            case WINDOWS -> {
                return "Sparrow-" + version;
            }
            default ->  {
                return "sparrow_" + version + "-1_" + (arch.equals("aarch64") ? "arm64" : arch);
            }
        }
    }

    private String getDisplayMessage(Throwable e) {
        String message = e.getMessage();
        message = message.substring(0, 1).toUpperCase(Locale.ROOT) + message.substring(1);

        if(message.endsWith(".")) {
            message = message.substring(0, message.length() - 1);
        }

        if(message.equals("Invalid header encountered")) {
            message += ", not a valid signature file";
        }

        if(message.startsWith("Malformed message")) {
            message = "Not a valid signature file";
        }

        return message;
    }

    public static boolean isVerifyDownloadFile(File file) {
        if(file != null) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if(name.length() > 4 && SIGNATURE_EXTENSIONS.stream().anyMatch(ext -> name.endsWith("." + ext))) {
                return true;
            }

            if(MANIFEST_EXTENSIONS.stream().anyMatch(ext -> name.endsWith("." + ext)) || name.startsWith(SHA256SUMS_MANIFEST_PREFIX)) {
                try {
                    Map<File, String> manifest = getManifest(file);
                    return !manifest.isEmpty();
                } catch(Exception e) {
                    //ignore
                }
            }

            if(name.startsWith(SPARROW_RELEASE_PREFIX) && file.length() >= MIN_VALID_SPARROW_RELEASE_SIZE) {
                Matcher matcher = SPARROW_RELEASE_VERSION.matcher(name);
                return matcher.find();
            }
        }

        return false;
    }

    public void setSignatureFile(File signatureFile) {
        signature.set(signatureFile);
    }

    private static class Header extends GridPane {
        public Header() {
            setMaxWidth(Double.MAX_VALUE);
            getStyleClass().add("header-panel");

            VBox vBox = new VBox();
            vBox.setPadding(new Insets(10, 0, 0, 0));

            Label headerLabel = new Label("Verify Download");
            headerLabel.setWrapText(true);
            headerLabel.setAlignment(Pos.CENTER_LEFT);
            headerLabel.setMaxWidth(Double.MAX_VALUE);
            headerLabel.setMaxHeight(Double.MAX_VALUE);

            CopyableLabel descriptionLabel = new CopyableLabel("Download the release file, GPG signature and optional manifest of a project to verify the download integrity");
            descriptionLabel.setAlignment(Pos.CENTER_LEFT);

            vBox.getChildren().addAll(headerLabel, descriptionLabel);
            add(vBox, 0, 0);

            StackPane graphicContainer = new StackPane();
            graphicContainer.getStyleClass().add("graphic-container");
            Image image = new Image("image/sparrow-small.png", 50, 50, false, false);
            if (!image.isError()) {
                ImageView imageView = new ImageView();
                imageView.setSmooth(false);
                imageView.setImage(image);
                graphicContainer.getChildren().add(imageView);
            }
            add(graphicContainer, 1, 0);

            ColumnConstraints textColumn = new ColumnConstraints();
            textColumn.setFillWidth(true);
            textColumn.setHgrow(Priority.ALWAYS);
            ColumnConstraints graphicColumn = new ColumnConstraints();
            graphicColumn.setFillWidth(false);
            graphicColumn.setHgrow(Priority.NEVER);
            getColumnConstraints().setAll(textColumn , graphicColumn);
        }
    }

    private static class FileField extends HBox {
        private final ObjectProperty<File> fileProperty;

        public FileField(ObjectProperty<File> fileProperty, String title, List<String> extensions, boolean optional, String example, BooleanProperty disabledProperty) {
            super(10);
            this.fileProperty = fileProperty;
            TextField textField = new TextField();
            textField.setEditable(false);
            textField.setPromptText("e.g. " + example + formatExtensionsList(extensions) + (optional ? " (optional)" : ""));
            textField.setOnMouseClicked(event -> browseForFile(title, extensions));
            Button browseButton = new Button("Browse...");
            browseButton.setOnAction(event -> browseForFile(title, extensions));
            getChildren().addAll(textField, browseButton);
            HBox.setHgrow(textField, Priority.ALWAYS);

            fileProperty.addListener((observable, oldValue, file) -> {
                textField.setText(file == null ? "" : file.getAbsolutePath());
                if(file != null) {
                    lastFileParent = file.getParentFile();
                }
            });

            if(disabledProperty != null) {
                disabledProperty.addListener((observable, oldValue, disabled) -> {
                    textField.setDisable(disabled);
                    browseButton.setDisable(disabled);
                });
            }
        }

        private void browseForFile(String title, List<String> extensions) {
            Stage window = new Stage();
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Open File");
            File userDir = new File(System.getProperty("user.home"));
            File downloadsDir = new File(userDir, "Downloads");
            fileChooser.setInitialDirectory(lastFileParent != null ? lastFileParent : (downloadsDir.exists() ? downloadsDir : userDir));
            fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter(title + " files", extensions));

            AppServices.moveToActiveWindowScreen(window, 800, 450);
            File file = fileChooser.showOpenDialog(window);
            if(file != null) {
                fileProperty.set(file);
            }
        }

        public String formatExtensionsList(List<String> items) {
            StringBuilder result = new StringBuilder();
            for(int i = 0; i < items.size(); i++) {
                result.append(".").append(items.get(i));

                if (i < items.size() - 1) {
                    result.append(", ");
                }

                if (i == items.size() - 2) {
                    result.append("or ");
                }
            }

            return result.toString();
        }
    }

    private static class PGPVerifyService extends Service<PGPVerificationResult> {
        private final File signature;
        private final File manifest;
        private final File publicKey;

        public PGPVerifyService(File signature, File manifest, File publicKey) {
            this.signature = signature;
            this.manifest = manifest;
            this.publicKey = publicKey;
        }

        @Override
        protected Task<PGPVerificationResult> createTask() {
            return new Task<>() {
                protected PGPVerificationResult call() throws IOException, PGPVerificationException {
                    boolean detachedSignature = !manifest.equals(signature);

                    try(InputStream publicKeyStream = publicKey == null ? null : new FileInputStream(publicKey);
                        InputStream contentStream = new BufferedInputStream(new FileInputStream(manifest));
                        InputStream detachedSignatureStream = detachedSignature ? new FileInputStream(signature) : null) {
                        return PGPUtils.verify(publicKeyStream, contentStream, detachedSignatureStream);
                    }
                }
            };
        }
    }

    private static class FileSha256Service extends Service<String> {
        private final File file;

        public FileSha256Service(File file) {
            this.file = file;
        }

        @Override
        protected Task<String> createTask() {
            return new Task<>() {
                protected String call() throws IOException {
                    try(InputStream inputStream = new BufferedInputStream(new FileInputStream(file))) {
                        return sha256(inputStream);
                    }
                }
            };
        }

        private String sha256(InputStream stream) throws IOException {
            try {
                final byte[] buffer = new byte[1024 * 1024];
                final MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                int bytesRead = 0;
                while((bytesRead = stream.read(buffer)) >= 0) {
                    if (bytesRead > 0) {
                        sha256.update(buffer, 0, bytesRead);
                    }
                }

                return Utils.bytesToHex(sha256.digest());
            } catch(NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static class InvalidManifestException extends Exception { }
}
