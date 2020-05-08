package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.io.KeystoreImport;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public abstract class KeystoreImportPane extends TitledPane {
    protected final KeystoreImportAccordion importAccordion;
    protected final Wallet wallet;

    private Label mainLabel;
    private Label descriptionLabel;
    protected Hyperlink showHideLink;
    protected HBox buttonBox;

    public KeystoreImportPane(KeystoreImportAccordion importAccordion, Wallet wallet, KeystoreImport importer) {
        this.importAccordion = importAccordion;
        this.wallet = wallet;

        setPadding(Insets.EMPTY);
        setGraphic(getTitle(importer));
        getStyleClass().add("importpane");
        setContent(getContentBox(importer.getKeystoreImportDescription()));
        removeArrow();
    }

    private void removeArrow() {
        Platform.runLater(() -> {
            Node arrow = this.lookup(".arrow");
            if (arrow != null) {
                arrow.setVisible(false);
                arrow.setManaged(false);
            } else {
                removeArrow();
            }
        });
    }

    protected Node getTitle(KeystoreImport importer) {
        HBox listItem = new HBox();
        listItem.setPadding(new Insets(10, 20, 10, 10));
        listItem.setSpacing(10);

        HBox imageBox = new HBox();
        imageBox.setMinWidth(50);
        imageBox.setMinHeight(50);
        listItem.getChildren().add(imageBox);

        Image image = new Image("image/" + importer.getWalletModel().getType() + ".png", 50, 50, true, true);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setImage(image);
            imageBox.getChildren().add(imageView);
        }

        VBox labelsBox = new VBox();
        labelsBox.setSpacing(5);
        labelsBox.setAlignment(Pos.CENTER_LEFT);
        this.mainLabel = new Label();
        mainLabel.setText(importer.getName());
        mainLabel.getStyleClass().add("main-label");
        labelsBox.getChildren().add(mainLabel);

        HBox descriptionBox = new HBox();
        descriptionBox.setSpacing(7);
        labelsBox.getChildren().add(descriptionBox);

        descriptionLabel = new Label("Keystore Import");
        descriptionLabel.getStyleClass().add("description-label");
        showHideLink = new Hyperlink("Show Details...");
        showHideLink.managedProperty().bind(showHideLink.visibleProperty());
        showHideLink.setOnAction(event -> {
            if(this.isExpanded()) {
                setExpanded(false);
            } else {
                setExpanded(true);
            }
        });
        this.expandedProperty().addListener((observable, oldValue, newValue) -> {
            if(newValue) {
                showHideLink.setText(showHideLink.getText().replace("Show", "Hide"));
            } else {
                showHideLink.setText(showHideLink.getText().replace("Hide", "Show"));
            }
        });
        descriptionBox.getChildren().addAll(descriptionLabel, showHideLink);

        listItem.getChildren().add(labelsBox);
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        listItem.getChildren().add(buttonBox);

        this.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
            //Hack to force listItem to expand to full available width less border
            listItem.setPrefWidth(newValue.getWidth() - 2);
        });

        return listItem;
    }

    protected void setDescription(String text) {
        descriptionLabel.getStyleClass().remove("description-error");
        descriptionLabel.getStyleClass().add("description-label");
        descriptionLabel.setText(text);
    }

    protected void setError(String title, String detail) {
        descriptionLabel.getStyleClass().remove("description-label");
        descriptionLabel.getStyleClass().add("description-error");
        descriptionLabel.setText(title);
        setContent(getContentBox(detail));
        setExpanded(true);
    }

    protected Node getContentBox(String message) {
        Label details = new Label(message);
        details.setWrapText(true);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.getChildren().add(details);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        double width = TextUtils.computeTextWidth(details.getFont(), message, 0.0D);
        double numLines = Math.max(1, width / 400);
        double height = Math.max(60, numLines * 40);
        contentBox.setPrefHeight(height);

        return contentBox;
    }
}
