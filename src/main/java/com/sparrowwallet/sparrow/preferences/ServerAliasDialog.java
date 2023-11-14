package com.sparrowwallet.sparrow.preferences;

import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.glyphfont.FontAwesome5;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.ServerType;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.util.converter.DefaultStringConverter;
import org.controlsfx.glyphfont.Glyph;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

public class ServerAliasDialog extends Dialog<Server> {
    private final ServerType serverType;
    private final TableView<ServerEntry> serverTable;
    private final Button closeButton;

    public ServerAliasDialog(ServerType serverType) {
        this.serverType = serverType;

        final DialogPane dialogPane = new ServerAliasDialogPane();
        setDialogPane(dialogPane);
        AppServices.setStageIcon(dialogPane.getScene().getWindow());

        setTitle("Server Aliases");
        dialogPane.getStylesheets().add(AppServices.class.getResource("general.css").toExternalForm());
        dialogPane.setHeaderText("Configure aliases for recently connected servers.\nNew servers are added to this list on successful connections.");

        Image image = new Image("/image/sparrow-small.png");
        dialogPane.setGraphic(new ImageView(image));

        serverTable = new TableView<>();
        serverTable.setPlaceholder(new Label("No servers added yet"));

        TableColumn<ServerEntry, String> urlColumn = new TableColumn<>("URL");
        urlColumn.setMinWidth(300);
        urlColumn.setCellValueFactory((TableColumn.CellDataFeatures<ServerEntry, String> param) -> {
            return new ReadOnlyObjectWrapper<>(param.getValue().getUrl());
        });

        TableColumn<ServerEntry, String> aliasColumn = new TableColumn<>("Alias");
        aliasColumn.setCellValueFactory((TableColumn.CellDataFeatures<ServerEntry, String> param) -> {
            return param.getValue().labelProperty();
        });
        aliasColumn.setCellFactory(value -> new AliasCell());
        aliasColumn.setGraphic(getTagGlyph());

        serverTable.getColumns().add(urlColumn);
        serverTable.getColumns().add(aliasColumn);
        serverTable.setEditable(true);
        serverTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        List<Server> servers = serverType == ServerType.BITCOIN_CORE ? Config.get().getRecentCoreServers() : Config.get().getRecentElectrumServers();
        List<ServerEntry> serverEntries = servers.stream().map(server -> new ServerEntry(serverType, server)).collect(Collectors.toList());
        serverTable.setItems(FXCollections.observableArrayList(serverEntries));

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(serverTable);
        dialogPane.setContent(stackPane);

        ButtonType selectButtonType = new ButtonType("Select", ButtonBar.ButtonData.APPLY);
        ButtonType deleteButtonType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
        dialogPane.getButtonTypes().addAll(deleteButtonType, ButtonType.CLOSE, selectButtonType);

        Button selectButton = (Button)dialogPane.lookupButton(selectButtonType);
        Button deleteButton = (Button)dialogPane.lookupButton(deleteButtonType);
        closeButton = (Button)dialogPane.lookupButton(ButtonType.CLOSE);
        selectButton.setDefaultButton(true);
        selectButton.setDisable(true);
        deleteButton.setDisable(true);
        serverTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            selectButton.setDisable(newValue == null);
            deleteButton.setDisable(newValue == null);
        });

        setResultConverter(buttonType -> buttonType == selectButtonType ? serverTable.getSelectionModel().getSelectedItem().getServer() : null);

        dialogPane.setPrefWidth(680);
        dialogPane.setPrefHeight(500);
        dialogPane.setMinHeight(dialogPane.getPrefHeight());
        AppServices.setStageIcon(dialogPane.getScene().getWindow());
        AppServices.moveToActiveWindowScreen(this);
    }

    private static Glyph getTagGlyph() {
        Glyph glyph = new Glyph(FontAwesome5.FONT_NAME, FontAwesome5.Glyph.TAG);
        glyph.setFontSize(12);
        return glyph;
    }

    private class ServerAliasDialogPane extends DialogPane {
        @Override
        protected Node createButton(ButtonType buttonType) {
            Node button;
            if(buttonType.getButtonData() == ButtonBar.ButtonData.LEFT) {
                Button deleteButton = new Button(buttonType.getText());
                final ButtonBar.ButtonData buttonData = buttonType.getButtonData();
                ButtonBar.setButtonData(deleteButton, buttonData);
                deleteButton.setOnAction(event -> {
                    ServerEntry serverEntry = serverTable.getSelectionModel().getSelectedItem();
                    if(serverEntry != null) {
                        serverTable.getItems().remove(serverEntry);
                        if(serverType == ServerType.BITCOIN_CORE) {
                            Config.get().removeRecentCoreServer(serverEntry.getServer());
                            if(serverEntry.getServer().equals(Config.get().getCoreServer()) && !serverTable.getItems().isEmpty()) {
                                closeButton.setDisable(true);
                            }
                        } else {
                            Config.get().removeRecentElectrumServer(serverEntry.getServer());
                            if(serverEntry.getServer().equals(Config.get().getElectrumServer()) && !serverTable.getItems().isEmpty()) {
                                closeButton.setDisable(true);
                            }
                        }
                    }
                });

                button = deleteButton;
            } else {
                button = super.createButton(buttonType);
            }

            return button;
        }
    }

    private static class ServerEntry {
        private final Server server;
        private final StringProperty labelProperty = new SimpleStringProperty();

        public ServerEntry(ServerType serverType, Server server) {
            this.server = server;
            labelProperty.set(server.getAlias());
            labelProperty.addListener((observable, oldValue, newValue) -> {
                String alias = newValue;
                if(alias != null && alias.isEmpty()) {
                    alias = null;
                }

                Server newServer = new Server(server.getUrl(), alias);
                if(serverType == ServerType.BITCOIN_CORE) {
                    Config.get().setCoreServerAlias(newServer);
                } else {
                    Config.get().setElectrumServerAlias(newServer);
                }
            });
        }

        public String getUrl() {
            return server.getHostAndPort().toString();
        }

        public StringProperty labelProperty() {
            return labelProperty;
        }

        public Server getServer() {
            return new Server(server.getUrl(), labelProperty.get() == null || labelProperty.get().isEmpty() ? null : labelProperty.get());
        }
    }

    private static class AliasCell extends TextFieldTableCell<ServerEntry, String> {
        public AliasCell() {
            super(new DefaultStringConverter());
        }

        @Override
        public void commitEdit(String label) {
            if(label != null) {
                label = label.trim();
            }

            // This block is necessary to support commit on losing focus, because
            // the baked-in mechanism sets our editing state to false before we can
            // intercept the loss of focus. The default commitEdit(...) method
            // simply bails if we are not editing...
            if (!isEditing() && !label.equals(getItem())) {
                TableView<ServerEntry> table = getTableView();
                if(table != null) {
                    TableColumn<ServerEntry, String> column = getTableColumn();
                    TableColumn.CellEditEvent<ServerEntry, String> event = new TableColumn.CellEditEvent<>(
                            table, new TablePosition<>(table, getIndex(), column),
                            TableColumn.editCommitEvent(), label
                    );
                    Event.fireEvent(column, event);
                }
            }

            super.commitEdit(label);
        }

        @Override
        public void startEdit() {
            super.startEdit();

            try {
                Field f = getClass().getSuperclass().getDeclaredField("textField");
                f.setAccessible(true);
                TextField textField = (TextField)f.get(this);
                textField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                    if (!isNowFocused) {
                        commitEdit(getConverter().fromString(textField.getText()));
                        setText(getConverter().fromString(textField.getText()));
                    }
                });
            } catch (Exception e) {
                //ignore
            }
        }
    }
}
