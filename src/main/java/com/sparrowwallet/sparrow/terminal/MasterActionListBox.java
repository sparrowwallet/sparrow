package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.terminal.preferences.GeneralDialog;
import com.sparrowwallet.sparrow.terminal.preferences.ServerStatusDialog;
import com.sparrowwallet.sparrow.terminal.preferences.ServerTypeDialog;
import com.sparrowwallet.sparrow.terminal.wallet.LoadWallet;
import javafx.application.Platform;

import java.io.File;
import java.util.Map;
import java.util.Optional;

public class MasterActionListBox extends ActionListBox {
    public static final int MAX_RECENT_WALLETS = 6;

    public MasterActionListBox(SparrowTerminal sparrowTerminal) {
        super(new TerminalSize(14, 3));

        addItem("Wallets", () -> {
           ActionListDialogBuilder builder = new ActionListDialogBuilder();
           builder.setTitle("Wallets");
           for(int i = 0; i < Config.get().getRecentWalletFiles().size() && i < MAX_RECENT_WALLETS; i++) {
               File recentWalletFile = Config.get().getRecentWalletFiles().get(i);
               Storage storage = new Storage(recentWalletFile);

               Optional<Wallet> optWallet = AppServices.get().getOpenWallets().entrySet().stream()
                       .filter(entry -> entry.getValue().getWalletFile().equals(recentWalletFile)).map(Map.Entry::getKey)
                       .map(wallet -> wallet.isMasterWallet() ? wallet : wallet.getMasterWallet()).findFirst();
               if(optWallet.isPresent()) {
                   builder.addAction(storage.getWalletName(null) + "*", () -> LoadWallet.getOpeningDialog(optWallet.get()).showDialog(SparrowTerminal.get().getGui()));
               } else {
                   builder.addAction(storage.getWalletName(null), new LoadWallet(storage));
               }
           }
           builder.addAction("Open Wallet...", () -> {
               FileDialogBuilder openBuilder = new FileDialogBuilder().setTitle("Open Wallet");
               openBuilder.setShowHiddenDirectories(true);
               openBuilder.setSelectedFile(Storage.getWalletsDir());
               File file = openBuilder.build().showDialog(SparrowTerminal.get().getGui());
               if(file != null) {
                   LoadWallet loadWallet = new LoadWallet(new Storage(file));
                   SparrowTerminal.get().getGuiThread().invokeLater(loadWallet);
               }
           });
           builder.build().showDialog(SparrowTerminal.get().getGui());
        });

        addItem("Preferences", () -> {
            new ActionListDialogBuilder()
                    .setTitle("Preferences")
                    .addAction("General", () -> {
                        GeneralDialog generalDialog = new GeneralDialog();
                        generalDialog.showDialog(sparrowTerminal.getGui());
                    })
                    .addAction("Server", () -> {
                        if(Config.get().hasServer()) {
                            ServerStatusDialog serverStatusDialog = new ServerStatusDialog();
                            serverStatusDialog.showDialog(sparrowTerminal.getGui());
                        } else {
                            ServerTypeDialog serverTypeDialog = new ServerTypeDialog();
                            serverTypeDialog.showDialog(sparrowTerminal.getGui());
                        }
                    })
                    .build()
                    .showDialog(sparrowTerminal.getGui());
        });

        addItem("Quit", () -> sparrowTerminal.getGui().getMainWindow().close());
    }
}
