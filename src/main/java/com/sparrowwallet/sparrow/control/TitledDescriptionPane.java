package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitledDescriptionPane extends TitledPane {
    private Label mainLabel;
    private Label descriptionLabel;
    protected Hyperlink showHideLink;
    protected HBox buttonBox;

    public TitledDescriptionPane(String title, String description, String content, WalletModel walletModel) {
        getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        getStyleClass().add("titled-description-pane");

        setPadding(Insets.EMPTY);
        setGraphic(getTitle(title, description, walletModel));
        setContent(getContentBox(content));
        removeArrow();
    }

    protected Node getTitle(String title, String description, WalletModel walletModel) {
        HBox listItem = new HBox();
        listItem.setPadding(new Insets(10, 20, 10, 10));
        listItem.setSpacing(10);

        HBox imageBox = new HBox();
        imageBox.setMinWidth(50);
        imageBox.setMinHeight(50);
        listItem.getChildren().add(imageBox);

        WalletModelImage walletModelImage = new WalletModelImage(walletModel);
        imageBox.getChildren().add(walletModelImage);

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
        // Create the VBox to hold text and Hyperlink components
        VBox contentBox = new VBox();
        contentBox.setAlignment(Pos.TOP_LEFT);
        contentBox.setPadding(new Insets(10, 30, 10, 30));
        contentBox.setPrefWidth(400);  // Set preferred width for wrapping
        contentBox.setMinHeight(60);

        // Define the regex pattern to match URLs
        String urlPattern = "(\\[https?://\\S+])";
        Pattern pattern = Pattern.compile(urlPattern);
        Matcher matcher = pattern.matcher(message);

        // StringBuilder to track the non-URL text
        int lastMatchEnd = 0;

        // Iterate through the matches and build the components
        while (matcher.find()) {
            // Add the text before the URL as a normal Label
            if (matcher.start() > lastMatchEnd) {
                String nonUrlText = message.substring(lastMatchEnd, matcher.start());
                Label textLabel = createWrappedLabel(nonUrlText);
                contentBox.getChildren().add(textLabel);
            }

            // Extract the URL and create a Hyperlink for it
            String url = matcher.group(1).replaceAll("\\[", "").replaceAll("\\]", "");
            Hyperlink hyperlink = createHyperlink(url);
            contentBox.getChildren().add(hyperlink);

            // Update last match end
            lastMatchEnd = matcher.end();
        }

        // Add remaining text after the last URL (if any)
        if (lastMatchEnd < message.length()) {
            String remainingText = message.substring(lastMatchEnd);
            Label remainingLabel = createWrappedLabel(remainingText);
            contentBox.getChildren().add(remainingLabel);
        }

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

    // Helper method to create a wrapped Label with a specified maxWidth
    private Label createWrappedLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(400);
        return label;
    }

    // Helper method to create a Hyperlink
    private Hyperlink createHyperlink(String url) {
        Hyperlink hyperlink = new Hyperlink(url);
        hyperlink.setMaxWidth(400);  // Set maximum width for wrapping
        hyperlink.setWrapText(true);  // Ensure text wrapping in the hyperlink
        hyperlink.setOnAction(_ -> AppServices.get().getApplication().getHostServices().showDocument(url));
        return hyperlink;
    }
}
