package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.control.WelcomeDialog;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.preferences.PreferenceGroup;
import com.sparrowwallet.sparrow.preferences.PreferencesDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class MainApp extends Application {
    public static final String APP_NAME = "Sparrow";

    private Stage mainStage;

    @Override
    public void init() throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> LoggerFactory.getLogger(MainApp.class).error("Exception in thread \"" + t.getName() + "\"", e));
        super.init();
    }

    @Override
    public void start(Stage stage) throws Exception {
        this.mainStage = stage;

        GlyphFontRegistry.register(new FontAwesome5());
        GlyphFontRegistry.register(new FontAwesome5Brands());

        boolean createNewWallet = false;
        Mode mode = Config.get().getMode();
        if(mode == null) {
            WelcomeDialog welcomeDialog = new WelcomeDialog(getHostServices());
            Optional<Mode> optionalMode = welcomeDialog.showAndWait();
            if(optionalMode.isPresent()) {
                mode = optionalMode.get();
                Config.get().setMode(mode);

                if(mode.equals(Mode.ONLINE)) {
                    PreferencesDialog preferencesDialog = new PreferencesDialog(PreferenceGroup.SERVER, true);
                    Optional<Boolean> optNewWallet = preferencesDialog.showAndWait();
                    createNewWallet = optNewWallet.isPresent() && optNewWallet.get();
                }
            }
        }

        FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        Parent root = transactionLoader.load();
        AppController appController = transactionLoader.getController();
        appController.setApplication(this);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        stage.setTitle("Sparrow");
        stage.setMinWidth(650);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/image/sparrow.png")));

        appController.initializeView();

        stage.show();

        if(createNewWallet) {
            appController.newWallet(null);
        }

        List<File> recentWalletFiles = Config.get().getRecentWalletFiles();
        if(recentWalletFiles != null) {
            for(File walletFile : recentWalletFiles) {
                if(walletFile.exists()) {
                    Platform.runLater(() -> appController.openWalletFile(walletFile));
                }
            }
        }
    }

    @Override
    public void stop() throws Exception {
        mainStage.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
