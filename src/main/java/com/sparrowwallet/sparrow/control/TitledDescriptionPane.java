package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.drongo.wallet.WalletModel;
import com.sparrowwallet.sparrow.AppServices;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitledDescriptionPane extends TitledPane {
    private static final String COLDCARD_SEED_WARNING_URL = "https://blog.coinkite.com/coldcard-mk3-seed-generation-warning/";
    private static final String COLDCARD_SEED_WARNING = "\u26A0 Security warning: Coldcard Mk2/Mk3 (firmware 4.0.1-4.1.9) and Mk4/Mk5/Q before their fixed firmware generated seeds with reduced entropy. If your seed was generated on-device with fewer than 50 independent dice rolls, update firmware, generate a new seed and move your funds:";

    private Label mainLabel;
    private Label descriptionLabel;
    private final BooleanProperty showingError = new SimpleBooleanProperty(false);

    //An action button disables itself while its operation runs, which would move focus on to the dialog's buttons. Focus stays on this
    //pane instead, whose name includes the status line, so the operation's progress and result are read.
    private final ChangeListener<Boolean> keepFocusWhenDisabled = (observable, wasDisabled, disabled) -> {
        Node node = (Node)((ReadOnlyProperty<?>)observable).getBean();
        if(disabled && node.getScene() != null && node.getScene().getFocusOwner() == node) {
            requestFocus();
        }
    };
    protected Hyperlink showHideLink;
    protected HBox buttonBox;

    public TitledDescriptionPane(String title, String description, String content, WalletModel walletModel) {
        getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        getStyleClass().add("titled-description-pane");

        setPadding(Insets.EMPTY);
        setGraphic(getTitle(title, description, walletModel));
        accessibleTextProperty().bind(Bindings.createStringBinding(() -> descriptionLabel.getText() == null || descriptionLabel.getText().isEmpty() ?
                mainLabel.getText() : mainLabel.getText() + ", " + descriptionLabel.getText(), mainLabel.textProperty(), descriptionLabel.textProperty()));
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
        //A read-only text field, as screen readers keep the first value of static text and the line changes with the pane's state
        descriptionLabel.setAccessibleRole(AccessibleRole.TEXT_FIELD);
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

        if(walletModel == WalletModel.COLDCARD) {
            Label warningLabel = createWrappedLabel(COLDCARD_SEED_WARNING);
            warningLabel.getStyleClass().add("description-error");
            Hyperlink warningLink = createHyperlink(COLDCARD_SEED_WARNING_URL);
            VBox warningBox = new VBox(warningLabel, warningLink);
            warningBox.setSpacing(2);
            labelsBox.getChildren().add(warningBox);
        }

        listItem.getChildren().add(labelsBox);
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        buttonBox = new HBox();
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addListener((ListChangeListener<Node>)change -> {
            while(change.next()) {
                change.getAddedSubList().forEach(this::keepFocusWhenDisabled);
            }
        });
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
        showingError.set(false);
        descriptionLabel.setAccessibleText(null);
        descriptionLabel.setFocusTraversable(false);
    }

    protected void setError(String title, String detail) {
        //Checked before the content is replaced, as removing the old content with focus in it clears focus from the pane straight away.
        //The window's focus owner is used rather than focusWithin, which is false while the window is inactive.
        boolean hadFocus = hasFocusOwner();
        descriptionLabel.getStyleClass().remove("description-label");
        if(!descriptionLabel.getStyleClass().contains("description-error")) {
            descriptionLabel.getStyleClass().add("description-error");
        }
        descriptionLabel.setText(title);
        showingError.set(true);
        if(detail != null && !detail.isEmpty()) {
            setContent(getContentBox(detail));
            setExpanded(true);
        }

        //The error line is read as a named, read-only field holding the whole error, links included. A screen reader doesn't speak the
        //new name of a pane that already has focus, so when focus is in this pane the error takes it to be read; focus elsewhere, such
        //as another device, is left alone.
        String spokenDetail = detail == null ? "" : detail.replaceAll("\\[(https?://\\S+)]", "$1").trim();
        descriptionLabel.setAccessibleText(spokenDetail.isEmpty() ? title : title + ": " + spokenDetail);
        descriptionLabel.setFocusTraversable(true);
        if(hadFocus) {
            Platform.runLater(descriptionLabel::requestFocus);
        }
    }

    //A PIN entry shown again after a wrong PIN takes focus for the next try, so the field is named after the error while one is shown,
    //which reads the error as focus arrives rather than the error line taking focus away from the field
    protected void labelPinField(TextInputControl pinField, Pane container) {
        Label pinLabel = new Label();
        pinLabel.textProperty().bind(Bindings.createStringBinding(() -> showingError.get() ? descriptionLabel.getText() + ", PIN" : "PIN",
                showingError, descriptionLabel.textProperty()));
        pinLabel.setLabelFor(pinField);
        pinLabel.setVisible(false);
        pinLabel.setManaged(false);
        container.getChildren().add(pinLabel);
    }

    private boolean hasFocusOwner() {
        for(Node node = getScene() == null ? null : getScene().getFocusOwner(); node != null; node = node.getParent()) {
            if(node == this) {
                return true;
            }
        }

        return false;
    }

    //Used for the button row, and for buttons in a pane's content that disable themselves, such as Enter PIN and Initialize
    protected void keepFocusWhenDisabled(Node node) {
        node.disabledProperty().removeListener(keepFocusWhenDisabled);
        node.disabledProperty().addListener(keepFocusWhenDisabled);
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

    protected static void setDefaultButton(ButtonBase button) {
        button.getStyleClass().add("default-button");
        if(button instanceof SplitMenuButton splitMenuButton) {
            for(MenuItem item : splitMenuButton.getItems()) {
                item.getStyleClass().add("default-button");
            }
            splitMenuButton.getItems().addListener((ListChangeListener<MenuItem>) c -> {
                while(c.next()) {
                    for(MenuItem item : c.getAddedSubList()) {
                        item.getStyleClass().add("default-button");
                    }
                }
            });
        }
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
