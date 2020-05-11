package com.sparrowwallet.sparrow;

import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.keystoreimport.KeystoreImportDialog;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5Brands;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.controlsfx.glyphfont.GlyphFontRegistry;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        GlyphFontRegistry.register(new FontAwesome5());
        GlyphFontRegistry.register(new FontAwesome5Brands());

        FXMLLoader transactionLoader = new FXMLLoader(getClass().getResource("app.fxml"));
        Parent root = transactionLoader.load();
        AppController appController = transactionLoader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        stage.setTitle("Sparrow");
        stage.setMinWidth(650);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.getIcons().add(new Image(MainApp.class.getResourceAsStream("/sparrow.png")));

        appController.initializeView();

        Wallet wallet = new Wallet();
        wallet.setPolicyType(PolicyType.SINGLE);
        wallet.setScriptType(ScriptType.P2WPKH);

        KeystoreImportDialog dlg = new KeystoreImportDialog(wallet);
        dlg.showAndWait();

        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
