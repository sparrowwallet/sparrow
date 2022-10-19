package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.*;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.StorageException;
import com.sparrowwallet.sparrow.io.WalletAndKey;
import com.sparrowwallet.sparrow.terminal.ModalDialog;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class LoadWallet implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(LoadWallet.class);
    private final Storage storage;
    private final ModalDialog loadingDialog;

    public LoadWallet(Storage storage) {
        this.storage = storage;
        this.loadingDialog = new ModalDialog(storage.getWalletName(null), "Loading...");
    }

    @Override
    public void run() {
        SparrowTerminal.get().getGui().addWindow(loadingDialog);

        try {
            if(!storage.isEncrypted()) {
                Platform.runLater(() -> {
                    Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage);
                    loadWalletService.setExecutor(Storage.LoadWalletService.getSingleThreadedExecutor());
                    loadWalletService.setOnSucceeded(workerStateEvent -> {
                        WalletAndKey walletAndKey = loadWalletService.getValue();
                        openWallet(storage, walletAndKey);
                    });
                    loadWalletService.setOnFailed(workerStateEvent -> {
                        SparrowTerminal.get().getGuiThread().invokeLater(() -> SparrowTerminal.get().getGui().removeWindow(loadingDialog));
                        Throwable exception = workerStateEvent.getSource().getException();
                        if(exception instanceof StorageException) {
                            showErrorDialog("Error Opening Wallet", exception.getMessage());
                        }
                    });
                    loadWalletService.start();
                });
            } else {
                TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
                builder.setDescription("Enter the password for\n" + storage.getWalletName(null));
                builder.setPasswordInput(true);

                String password = builder.build().showDialog(SparrowTerminal.get().getGui());
                if(password == null) {
                    SparrowTerminal.get().getGui().removeWindow(loadingDialog);
                    return;
                }

                Platform.runLater(() -> {
                    Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage, new SecureString(password));
                    loadWalletService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Done"));
                        WalletAndKey walletAndKey = loadWalletService.getValue();
                        openWallet(storage, walletAndKey);
                    });
                    loadWalletService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Failed"));
                        SparrowTerminal.get().getGuiThread().invokeLater(() -> SparrowTerminal.get().getGui().removeWindow(loadingDialog));
                        Throwable exception = loadWalletService.getException();
                        if(exception instanceof InvalidPasswordException) {
                            Optional<ButtonType> optResponse = showErrorDialog("Invalid Password", "The wallet password was invalid. Try again?", ButtonType.CANCEL, ButtonType.OK);
                            if(optResponse.isPresent() && optResponse.get().equals(ButtonType.OK)) {
                                run();
                            }
                        } else {
                            if(exception instanceof StorageException) {
                                showErrorDialog("Error Opening Wallet", exception.getMessage());
                            }
                        }
                    });
                    EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.START, "Decrypting wallet..."));
                    loadWalletService.start();
                });
            }
        } catch(Exception e) {
            if(e instanceof IOException && e.getMessage().startsWith("The process cannot access the file because another process has locked")) {
                showErrorDialog("Error Opening Wallet", "The wallet file is locked. Is another instance of " + SparrowWallet.APP_NAME + " already running?");
            } else {
                log.error("Error opening wallet", e);
                showErrorDialog("Error Opening Wallet", e.getMessage() == null ? "Unsupported file format" : e.getMessage());
            }
        }
    }

    private void openWallet(Storage storage, WalletAndKey walletAndKey) {
        try {
            storage.restorePublicKeysFromSeed(walletAndKey.getWallet(), walletAndKey.getKey());
            if(!walletAndKey.getWallet().isValid()) {
                throw new IllegalStateException("Wallet file is not valid.");
            }
            SparrowTerminal.addWallet(storage, walletAndKey.getWallet());
            for(Map.Entry<WalletAndKey, Storage> entry : walletAndKey.getChildWallets().entrySet()) {
                openWallet(entry.getValue(), entry.getKey());
            }
            if(walletAndKey.getWallet().isMasterWallet()) {
                SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                    SparrowTerminal.get().getGui().removeWindow(loadingDialog);
                    getOpeningDialog(storage, walletAndKey.getWallet()).showDialog(SparrowTerminal.get().getGui());
                });
            }
        } catch(Exception e) {
            log.error("Wallet Error", e);
            showErrorDialog("Wallet Error", e.getMessage());
        } finally {
            walletAndKey.clear();
        }
    }

    public static DialogWindow getOpeningDialog(Storage storage, Wallet masterWallet) {
        if(masterWallet.getChildWallets().stream().anyMatch(childWallet -> !childWallet.isNested())) {
            return new WalletAccountsDialog(storage.getWalletId(masterWallet));
        } else {
            return new WalletActionsDialog(storage.getWalletId(masterWallet));
        }
    }
}
