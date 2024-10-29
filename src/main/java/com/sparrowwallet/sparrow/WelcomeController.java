package com.sparrowwallet.sparrow;

import com.sparrowwallet.sparrow.control.UnlabeledToggleSwitch;
import com.sparrowwallet.sparrow.event.LanguageChangedInWelcomeEvent;
import com.sparrowwallet.sparrow.i18n.Language;
import com.sparrowwallet.sparrow.i18n.LanguagesManager;
import com.sparrowwallet.sparrow.io.Config;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.controlsfx.control.StatusBar;

import java.util.Arrays;
import java.util.List;

public class WelcomeController {
    @FXML
    private VBox welcomeBox;

    @FXML
    private VBox step1;

    @FXML
    private VBox step2;

    @FXML
    private VBox step3;

    @FXML
    private VBox step4;

    @FXML
    private StatusBar serverStatus;

    @FXML
    private ComboBox<Language> languages;

    @FXML
    private UnlabeledToggleSwitch serverToggle;

    public void initializeView(boolean isFirstExecution) {
        step1.managedProperty().bind(step1.visibleProperty());
        step2.managedProperty().bind(step2.visibleProperty());
        step3.managedProperty().bind(step3.visibleProperty());
        step4.managedProperty().bind(step4.visibleProperty());

        step2.setVisible(false);
        step3.setVisible(false);
        step4.setVisible(false);

        welcomeBox.getStyleClass().add("offline");
        serverStatus.setText(LanguagesManager.getMessage("welcome.offline"));
        serverToggle.addEventFilter(MouseEvent.MOUSE_RELEASED, Event::consume);
        Tooltip tooltip = new Tooltip(LanguagesManager.getMessage("welcome.offline.tooltip"));
        tooltip.setShowDelay(Duration.ZERO);
        serverToggle.setTooltip(tooltip);
        serverToggle.selectedProperty().addListener((observable, oldValue, newValue) -> {
            serverStatus.setText(newValue ? LanguagesManager.getMessage("welcome.server-status.online") : LanguagesManager.getMessage("welcome.offline"));
        });

        if(isFirstExecution) {
            languages.setItems(getLanguagesList());
            languages.setConverter(new StringConverter<>() {
                @Override
                public String toString(Language language) {
                    if(language != null) {
                        return LanguagesManager.getMessage("language." + language.getCode());
                    } else {
                        return null;
                    }
                }

                @Override
                public Language fromString(String code) {
                    return Language.getFromCode(code);
                }
            });

            Language configuredLanguage = Config.get().getLanguage();
            if(configuredLanguage  != null) {
                languages.setValue(configuredLanguage);
            } else {
                languages.setValue(LanguagesManager.DEFAULT_LANGUAGE);
            }

            languages.valueProperty().addListener((observable, oldValue, newValue) -> {
                if(Config.get().getLanguage() != newValue) {
                    Config.get().setLanguage(newValue);
                    LanguagesManager.loadLanguage(newValue.getCode());

                    EventManager.get().post(new LanguageChangedInWelcomeEvent(newValue));
                }
            });
        } else {
            languages.setVisible(false);
        }
    }

    public boolean next() {
        if(step1.isVisible()) {
            step1.setVisible(false);
            step2.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("public-electrum");
            PauseTransition wait = new PauseTransition(Duration.millis(200));
            wait.setOnFinished((e) -> {
                serverToggle.setSelected(true);
                serverStatus.setText(LanguagesManager.getMessage("welcome.server-status.online.public-server"));
            });
            wait.play();
            return true;
        }

        if(step2.isVisible()) {
            step2.setVisible(false);
            step3.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("bitcoin-core");
            serverToggle.setSelected(true);
            serverStatus.setText(LanguagesManager.getMessage("welcome.server-status.online.bitcoin-core"));
            return true;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step4.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("private-electrum");
            serverToggle.setSelected(true);
            serverStatus.setText(LanguagesManager.getMessage("welcome.server-status.online.private-electrum"));
        }

        return false;
    }

    public boolean back() {
        if(step2.isVisible()) {
            step2.setVisible(false);
            step1.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("offline");
            PauseTransition wait = new PauseTransition(Duration.millis(200));
            wait.setOnFinished((e) -> {
                serverToggle.setSelected(false);
                serverStatus.setText(LanguagesManager.getMessage("welcome.offline"));
            });
            wait.play();
            return false;
        }

        if(step3.isVisible()) {
            step3.setVisible(false);
            step2.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("public-electrum");
            serverToggle.setSelected(true);
            serverStatus.setText(LanguagesManager.getMessage("welcome.server-status.online.public-server"));
            return true;
        }

        if(step4.isVisible()) {
            step4.setVisible(false);
            step3.setVisible(true);
            welcomeBox.getStyleClass().clear();
            welcomeBox.getStyleClass().add("bitcoin-core");
            serverToggle.setSelected(true);
            serverStatus.setText(LanguagesManager.getMessage("welcome.server-status.online.bitcoin-core"));
            return true;
        }

        return false;
    }

    private ObservableList<Language> getLanguagesList() {
        List<Language> languages = Arrays.asList(Language.values());
        return FXCollections.observableList(languages);
    }
}