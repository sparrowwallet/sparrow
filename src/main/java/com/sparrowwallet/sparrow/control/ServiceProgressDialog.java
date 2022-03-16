package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.sparrow.AppServices;
import javafx.beans.property.*;
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

    public static class ProxyWorker implements Worker<Boolean> {
        private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", State.READY);
        private final StringProperty message = new SimpleStringProperty(this, "message", "");
        private final DoubleProperty progress = new SimpleDoubleProperty(this, "progress", -1);

        public void start() {
            state.set(State.SCHEDULED);
        }

        public void end() {
            state.set(State.SUCCEEDED);
        }

        @Override
        public State getState() {
            return state.get();
        }

        @Override
        public ReadOnlyObjectProperty<State> stateProperty() {
            return state;
        }

        @Override
        public Boolean getValue() {
            return Boolean.TRUE;
        }

        @Override
        public ReadOnlyObjectProperty<Boolean> valueProperty() {
            return null;
        }

        @Override
        public Throwable getException() {
            return null;
        }

        @Override
        public ReadOnlyObjectProperty<Throwable> exceptionProperty() {
            return null;
        }

        @Override
        public double getWorkDone() {
            return 0;
        }

        @Override
        public ReadOnlyDoubleProperty workDoneProperty() {
            return null;
        }

        @Override
        public double getTotalWork() {
            return 0;
        }

        @Override
        public ReadOnlyDoubleProperty totalWorkProperty() {
            return null;
        }

        @Override
        public double getProgress() {
            return progress.get();
        }

        @Override
        public ReadOnlyDoubleProperty progressProperty() {
            return progress;
        }

        @Override
        public boolean isRunning() {
            return false;
        }

        @Override
        public ReadOnlyBooleanProperty runningProperty() {
            return null;
        }

        @Override
        public String getMessage() {
            return message.get();
        }

        public void setMessage(String strMessage) {
            message.set(strMessage);
        }

        @Override
        public ReadOnlyStringProperty messageProperty() {
            return message;
        }

        @Override
        public String getTitle() {
            return null;
        }

        @Override
        public ReadOnlyStringProperty titleProperty() {
            return null;
        }

        @Override
        public boolean cancel() {
            return false;
        }
    }
}
