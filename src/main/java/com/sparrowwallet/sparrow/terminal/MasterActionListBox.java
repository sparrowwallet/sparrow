package com.sparrowwallet.sparrow.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.dialogs.ActionListDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.FileDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.terminal.preferences.GeneralDialog;
import com.sparrowwallet.sparrow.terminal.preferences.ServerStatusDialog;
import com.sparrowwallet.sparrow.terminal.preferences.ServerTypeDialog;
import com.sparrowwallet.sparrow.terminal.wallet.Bip39Dialog;
import com.sparrowwallet.sparrow.terminal.wallet.LoadWallet;
import com.sparrowwallet.sparrow.terminal.wallet.WatchOnlyDialog;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.Optional;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class MasterActionListBox extends ActionListBox {
    private static final Logger log = LoggerFactory.getLogger(MasterActionListBox.class);

    public static final int MAX_RECENT_WALLETS = 6;

    public MasterActionListBox(SparrowTerminal sparrowTerminal) {
        super(new TerminalSize(14, 3));

        addItem("Wallets", () -> {
            ActionListDialogBuilder builder = new ActionListDialogBuilder();
            builder.setTitle("Wallets");
            if(Config.get().getRecentWalletFiles() != null) {
                for(int i = 0; i < Config.get().getRecentWalletFiles().size() && i < MAX_RECENT_WALLETS; i++) {
                    File recentWalletFile = Config.get().getRecentWalletFiles().get(i);
                    if(!recentWalletFile.exists()) {
                        continue;
                    }

                    Storage storage = new Storage(recentWalletFile);

                    Optional<Wallet> optWallet = AppServices.get().getOpenWallets().entrySet().stream()
                            .filter(entry -> entry.getValue().getWalletFile().equals(recentWalletFile)).map(Map.Entry::getKey)
                            .map(wallet -> wallet.isMasterWallet() ? wallet : wallet.getMasterWallet()).findFirst();
                    if(optWallet.isPresent()) {
                        Wallet wallet = optWallet.get();
                        Storage existingStorage = AppServices.get().getOpenWallets().get(wallet);
                        builder.addAction(storage.getWalletName(null) + "*", () -> openLoadedWallet(existingStorage, optWallet.get()));
                    } else {
                        builder.addAction(storage.getWalletName(null), new LoadWallet(storage));
                    }
                }
            }
            builder.addAction("Open Wallet...", () -> {
                SparrowTerminal.get().getGuiThread().invokeLater(MasterActionListBox::openWallet);
            });
            builder.addAction("Create Wallet...", () -> {
                SparrowTerminal.get().getGuiThread().invokeLater(MasterActionListBox::createWallet);
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

    private static void openLoadedWallet(Storage storage, Wallet wallet) {
        if(SparrowTerminal.get().isLocked(storage)) {
            String walletId = storage.getWalletId(wallet);

            TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
            builder.setDescription("Enter the wallet password:");
            builder.setPasswordInput(true);

            String password = builder.build().showDialog(SparrowTerminal.get().getGui());
            if(password != null) {
                Platform.runLater(() -> {
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, new SecureString(password), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                        keyDerivationService.getValue().clear();
                        SparrowTerminal.get().unlockWallet(storage);
                        SparrowTerminal.get().getGuiThread().invokeLater(() -> LoadWallet.getOpeningDialog(storage, wallet).showDialog(SparrowTerminal.get().getGui()));
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Failed"));
                        if(keyDerivationService.getException() instanceof InvalidPasswordException) {
                            showErrorDialog("Invalid Password", "The wallet password was invalid.");
                        } else {
                            log.error("Error deriving wallet key", keyDerivationService.getException());
                        }
                    });
                    EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.START, "Decrypting wallet..."));
                    keyDerivationService.start();
                });
            }
        } else {
            SparrowTerminal.get().getGuiThread().invokeLater(() -> LoadWallet.getOpeningDialog(storage, wallet).showDialog(SparrowTerminal.get().getGui()));
        }
    }

    private static void openWallet() {
        FileDialogBuilder openBuilder = new FileDialogBuilder().setTitle("Open Wallet");
        openBuilder.setShowHiddenDirectories(true);
        openBuilder.setSelectedFile(Storage.getWalletsDir());
        File file = openBuilder.build().showDialog(SparrowTerminal.get().getGui());
        if(file != null) {
            LoadWallet loadWallet = new LoadWallet(new Storage(file));
            SparrowTerminal.get().getGuiThread().invokeLater(loadWallet);
        }
    }

    private static void createWallet() {
        TextInputDialogBuilder newWalletNameBuilder = new TextInputDialogBuilder();
        newWalletNameBuilder.setTitle("Create Wallet");
        newWalletNameBuilder.setDescription("Enter a name for the wallet");
        newWalletNameBuilder.setValidator(content -> content.isEmpty() ? "Please enter a name" : (Storage.walletExists(content) ? "Wallet already exists" : null));
        String walletName = newWalletNameBuilder.build().showDialog(SparrowTerminal.get().getGui());

        if(walletName != null) {
            ActionListDialogBuilder newBuilder = new ActionListDialogBuilder();
            newBuilder.setTitle("Create Wallet");
            newBuilder.setDescription("Choose the type of wallet");
            newBuilder.addAction("Software (BIP39)", () -> {
                Bip39Dialog bip39Dialog = new Bip39Dialog(walletName);
                bip39Dialog.showDialog(SparrowTerminal.get().getGui());
            });
            newBuilder.addAction("Watch Only", () -> {
                WatchOnlyDialog watchOnlyDialog = new WatchOnlyDialog(walletName);
                watchOnlyDialog.showDialog(SparrowTerminal.get().getGui());
            });
            newBuilder.build().showDialog(SparrowTerminal.get().getGui());
        }
    }
}
