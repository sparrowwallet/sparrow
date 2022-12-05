package com.sparrowwallet.sparrow.terminal.preferences;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.DirectoryDialogBuilder;
import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Server;
import com.sparrowwallet.sparrow.net.CoreAuthType;
import com.sparrowwallet.sparrow.net.Protocol;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;

import java.io.File;
import java.util.List;

public class BitcoinCoreDialog extends ServerUrlDialog {
    private final ComboBox<String> authentication;
    private final Label dataFolderLabel;
    private final TextBox dataFolder;
    private final Button selectDataFolder;
    private final Label userPassLabel;
    private final TextBox user;
    private final TextBox pass;

    public BitcoinCoreDialog() {
        super("Bitcoin Core");

        setHints(List.of(Hint.CENTERED));
        Panel mainPanel = new Panel(new GridLayout(3).setHorizontalSpacing(2).setVerticalSpacing(0));

        if(Config.get().getCoreServer() == null) {
            Config.get().setCoreServer(new Server("http://127.0.0.1:" + Network.get().getDefaultPort()));
        }
        addUrlComponents(mainPanel, Config.get().getRecentCoreServers(), Config.get().getCoreServer());
        addLine(mainPanel);

        mainPanel.addComponent(new Label("Authentication"));
        authentication = new ComboBox<>("Default", "User/Pass");
        authentication.setSelectedIndex(Config.get().getCoreAuthType() == CoreAuthType.USERPASS ? 0 : 1);
        mainPanel.addComponent(authentication);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        dataFolderLabel = new Label("Data Folder");
        mainPanel.addComponent(dataFolderLabel);
        dataFolder = new TextBox(new TerminalSize(30, 1), Config.get().getCoreDataDir() != null ? Config.get().getCoreDataDir().getAbsolutePath() : "");
        mainPanel.addComponent(dataFolder);
        selectDataFolder = new Button("Select...");
        mainPanel.addComponent(selectDataFolder);

        userPassLabel = new Label("User/Pass");
        mainPanel.addComponent(userPassLabel);
        user = new TextBox(new TerminalSize(16, 1));
        mainPanel.addComponent(user);
        pass = new TextBox(new TerminalSize(16, 1));
        pass.setMask('*');
        mainPanel.addComponent(pass);

        if(Config.get().getCoreAuth() != null) {
            String[] userPass = Config.get().getCoreAuth().split(":");
            if(userPass.length > 0) {
                user.setText(userPass[0]);
            }
            if(userPass.length > 1) {
                pass.setText(userPass[1]);
            }
        }

        authentication.addListener((selectedIndex, previousSelection, changedByUserInteraction) -> {
            dataFolderLabel.setVisible(selectedIndex == 0);
            dataFolder.setVisible(selectedIndex == 0);
            selectDataFolder.setVisible(selectedIndex == 0);
            userPassLabel.setVisible(selectedIndex == 1);
            user.setVisible(selectedIndex == 1);
            pass.setVisible(selectedIndex == 1);
            Config.get().setCoreAuthType(selectedIndex == 0 ? CoreAuthType.COOKIE : CoreAuthType.USERPASS);
        });
        authentication.setSelectedIndex(Config.get().getCoreAuthType() == CoreAuthType.USERPASS ? 1 : 0);

        dataFolder.setTextChangeListener((newText, changedByUserInteraction) -> {
            File dataDir = new File(newText);
            if(dataDir.exists()) {
                Config.get().setCoreDataDir(dataDir);
            }
        });
        selectDataFolder.addListener(button -> {
            DirectoryDialogBuilder builder = new DirectoryDialogBuilder().setTitle("Select Bitcoin Core Data Folder").setActionLabel("Select");
            builder.setShowHiddenDirectories(true);
            builder.setSelectedDirectory(Config.get().getCoreDataDir() == null ? new File(System.getProperty("user.home")) : Config.get().getCoreDataDir());
            File file = builder.build().showDialog(SparrowTerminal.get().getGui());
            if(file != null) {
                dataFolder.setText(file.getAbsolutePath());
            }
        });
        user.setTextChangeListener((newText, changedByUserInteraction) -> {
            setCoreAuth();
        });
        pass.setTextChangeListener((newText, changedByUserInteraction) -> {
            setCoreAuth();
        });

        addLine(mainPanel);
        addProxyComponents(mainPanel);

        Panel buttonPanel = new Panel();
        buttonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        buttonPanel.addComponent(new Button("Test", this::onTest));
        buttonPanel.addComponent(new Button("Done", this::onDone));

        addLine(mainPanel);
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        buttonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    protected void setServerConfig() {
        Server currentServer = getCurrentServer();
        if(currentServer != null) {
            Config.get().setCoreServer(currentServer);
        }
    }

    protected void setServerAlias(Server server) {
        Config.get().setCoreServerAlias(server);
    }

    protected Protocol getProtocol() {
        Integer portAsInteger = getServerPort();
        return portAsInteger != null && portAsInteger == Protocol.HTTPS.getDefaultPort() ? Protocol.HTTPS : Protocol.HTTP;
    }

    protected void setProtocol(Protocol protocol) {
        //empty
    }

    private void setCoreAuth() {
        Config.get().setCoreAuth(user.getText() + ":" + pass.getText());
    }
}
