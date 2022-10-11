package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.*;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.SparrowWallet;
import com.sparrowwallet.sparrow.TabData;
import com.sparrowwallet.sparrow.WalletTabData;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Config;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.StorageException;
import com.sparrowwallet.sparrow.io.WalletAndKey;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;
import static com.sparrowwallet.sparrow.terminal.MasterActionListBox.MAX_RECENT_WALLETS;

public class LoadWallet implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(LoadWallet.class);
    private final Storage storage;
    private final LoadingDialog loadingDialog;

    public LoadWallet(Storage storage) {
        this.storage = storage;
        this.loadingDialog = new LoadingDialog(storage);
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
                        SparrowTerminal.get().getGuiThread().invokeLater(() -> openWallet(storage, walletAndKey));
                    });
                    loadWalletService.setOnFailed(workerStateEvent -> {
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
                    return;
                }

                Platform.runLater(() -> {
                    Storage.LoadWalletService loadWalletService = new Storage.LoadWalletService(storage, new SecureString(password));
                    loadWalletService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Done"));
                        WalletAndKey walletAndKey = loadWalletService.getValue();
                        SparrowTerminal.get().getGuiThread().invokeLater(() -> openWallet(storage, walletAndKey));
                    });
                    loadWalletService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(storage.getWalletId(null), TimedEvent.Action.END, "Failed"));
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
        SparrowTerminal.get().getGui().removeWindow(loadingDialog);

        try {
            storage.restorePublicKeysFromSeed(walletAndKey.getWallet(), walletAndKey.getKey());
            if(!walletAndKey.getWallet().isValid()) {
                throw new IllegalStateException("Wallet file is not valid.");
            }
            addWallet(storage, walletAndKey.getWallet());
            for(Map.Entry<WalletAndKey, Storage> entry : walletAndKey.getChildWallets().entrySet()) {
                openWallet(entry.getValue(), entry.getKey());
            }
            if(walletAndKey.getWallet().isMasterWallet()) {
                getOpeningDialog(walletAndKey.getWallet()).showDialog(SparrowTerminal.get().getGui());
            }
        } catch(Exception e) {
            log.error("Wallet Error", e);
            showErrorDialog("Wallet Error", e.getMessage());
        } finally {
            walletAndKey.clear();
        }
    }

    private void addWallet(Storage storage, Wallet wallet) {
        Platform.runLater(() -> {
            if(wallet.isNested()) {
                WalletData walletData = SparrowTerminal.get().getWalletData().get(wallet.getMasterWallet());
                WalletForm walletForm = new WalletForm(storage, wallet);
                EventManager.get().register(walletForm);
                walletData.getWalletForm().getNestedWalletForms().add(walletForm);
            } else {
                EventManager.get().post(new WalletOpeningEvent(storage, wallet));

                WalletForm walletForm = new WalletForm(storage, wallet);
                EventManager.get().register(walletForm);
                SparrowTerminal.get().getWalletData().put(wallet, new WalletData(walletForm));

                List<WalletTabData> walletTabDataList = SparrowTerminal.get().getWalletData().values().stream()
                        .map(data -> new WalletTabData(TabData.TabType.WALLET, data.getWalletForm())).collect(Collectors.toList());
                EventManager.get().post(new OpenWalletsEvent(DEFAULT_WINDOW, walletTabDataList));

                Set<File> walletFiles = new LinkedHashSet<>();
                walletFiles.add(storage.getWalletFile());
                walletFiles.addAll(Config.get().getRecentWalletFiles().stream().limit(MAX_RECENT_WALLETS - 1).collect(Collectors.toList()));
                Config.get().setRecentWalletFiles(Config.get().isLoadRecentWallets() ? new ArrayList<>(walletFiles) : Collections.emptyList());
            }

            EventManager.get().post(new WalletOpenedEvent(storage, wallet));
        });
    }

    public static DialogWindow getOpeningDialog(Wallet masterWallet) {
        if(masterWallet.getChildWallets().stream().anyMatch(childWallet -> !childWallet.isNested())) {
            return new WalletAccountsDialog(masterWallet);
        } else {
            return new WalletActionsDialog(masterWallet);
        }
    }

    private static final javafx.stage.Window DEFAULT_WINDOW = new Window() { };

    private static final class LoadingDialog extends DialogWindow {
        public LoadingDialog(Storage storage) {
            super(storage.getWalletName(null));

            setHints(List.of(Hint.CENTERED));
            setFixedSize(new TerminalSize(30, 5));

            Panel mainPanel = new Panel();
            mainPanel.setLayoutManager(new LinearLayout());
            mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

            Label label = new Label("Loading...");
            mainPanel.addComponent(label, LinearLayout.createLayoutData(LinearLayout.Alignment.Center));

            mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

            setComponent(mainPanel);
        }
    }
}
