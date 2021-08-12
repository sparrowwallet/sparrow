package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.concurrent.Worker;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.controlsfx.dialog.ProgressDialog;

public class ServiceProgressDialog extends ProgressDialog {
    public ServiceProgressDialog(String title, String header, String imagePath, Worker<?> worker) {
        super(worker);

        final DialogPane dialogPane = getDialogPane();
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        setTitle(title);
        setHeaderText(header);

        dialogPane.getStyleClass().remove("progress-dialog");
        Image image = new Image(imagePath);
        dialogPane.setGraphic(new ImageView(image));
    }
}
