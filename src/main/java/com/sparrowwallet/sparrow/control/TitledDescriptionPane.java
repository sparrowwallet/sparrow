package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Arrays;
import java.util.OptionalDouble;

public class TitledDescriptionPane extends TitledPane {
    private Label mainLabel;
    private Label descriptionLabel;
    protected Hyperlink showHideLink;
    protected HBox buttonBox;

    public TitledDescriptionPane(String title, String description, String content, String imageUrl) {
        getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        getStyleClass().add("titled-description-pane");

        setPadding(Insets.EMPTY);
        setGraphic(getTitle(title, description, imageUrl));
        setContent(getContentBox(content));
        removeArrow();
    }

    protected Node getTitle(String title, String description, String imageUrl) {
        HBox listItem = new HBox();
        listItem.setPadding(new Insets(10, 20, 10, 10));
        listItem.setSpacing(10);

        HBox imageBox = new HBox();
        imageBox.setMinWidth(50);
        imageBox.setMinHeight(50);
        listItem.getChildren().add(imageBox);

        Image image = new Image(imageUrl, 50, 50, true, true);
        if (!image.isError()) {
            ImageView imageView = new ImageView();
            imageView.setImage(image);
            imageBox.getChildren().add(imageView);
        }

        VBox labelsBox = new VBox();
        labelsBox.setSpacing(5);
        labelsBox.setAlignment(Pos.CENTER_LEFT);
        mainLabel = new Label();
        mainLabel.setText(title);
        mainLabel.getStyleClass().add("main-label");
        labelsBox.getChildren().add(mainLabel);

        HBox descriptionBox = new HBox();
        descriptionBox.setSpacing(7);
        labelsBox.getChildren().add(descriptionBox);

        descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("description-label");
        showHideLink = new Hyperlink("Details...");
        showHideLink.managedProperty().bind(showHideLink.visibleProperty());
        showHideLink.setOnAction(event -> {
            setExpanded(!this.isExpanded());
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
        Control button = createButton();
        if(button != null) {
            buttonBox.getChildren().add(button);
        }
        listItem.getChildren().add(buttonBox);

        this.layoutBoundsProperty().addListener((observable, oldValue, newValue) -> {
            //Hack to force listItem to expand to full available width less border
            listItem.setPrefWidth(newValue.getWidth() - 3);
        });

        return listItem;
    }

    protected Control createButton() {
        //No buttons by default
        return null;
    }

    public String getTitle() {
        return mainLabel.getText();
    }

    protected void setDescription(String text) {
        descriptionLabel.getStyleClass().remove("description-error");
        if(!descriptionLabel.getStyleClass().contains("description-label")) {
            descriptionLabel.getStyleClass().add("description-label");
        }
        descriptionLabel.setText(text);
    }

    protected void setError(String title, String detail) {
        descriptionLabel.getStyleClass().remove("description-label");
        if(!descriptionLabel.getStyleClass().contains("description-error")) {
            descriptionLabel.getStyleClass().add("description-error");
        }
        descriptionLabel.setText(title);
        if(detail != null && !detail.isEmpty()) {
            setContent(getContentBox(detail));
            setExpanded(true);
        }
    }

    protected Node getContentBox(String message) {
        Label details = new Label(message);
        details.setWrapText(true);

        HBox contentBox = new HBox();
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.getChildren().add(details);
        contentBox.setPadding(new Insets(10, 30, 10, 30));

        double width = TextUtils.computeTextWidth(details.getFont(), message, 0.0D);
        double numLines = Math.max(1, Math.ceil(width / 400d));

        //Handle long words like txids
        OptionalDouble maxWordLength = Arrays.stream(message.split(" ")).mapToDouble(word -> TextUtils.computeTextWidth(details.getFont(), message, 0.0D)).max();
        if(maxWordLength.isPresent() && maxWordLength.getAsDouble() > 300.0) {
            numLines += 1.0;
        }

        double height = Math.max(60, numLines * 20);
        contentBox.setPrefHeight(height);

        return contentBox;
    }

    private void removeArrow() {
        removeArrow(0);
    }

    private void removeArrow(int count) {
        Platform.runLater(() -> {
            Node arrow = this.lookup(".arrow");
            if (arrow != null) {
                arrow.setVisible(false);
                arrow.setManaged(false);
            } else if(count < 20) {
                removeArrow(count+1);
            }
        });
    }

    protected static int getAccount(Wallet wallet, KeyDerivation requiredDerivation) {
        if(wallet == null || requiredDerivation == null) {
            return 0;
        }

        int account = wallet.getScriptType().getAccount(requiredDerivation.getDerivationPath());
        if(account < 0) {
            account = 0;
        }

        return account;
    }
}
