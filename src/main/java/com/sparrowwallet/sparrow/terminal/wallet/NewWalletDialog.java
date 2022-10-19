package com.sparrowwallet.sparrow.terminal.wallet;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.EmptySpace;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.dialogs.DialogWindow;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.wallet.MnemonicException;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.StorageEvent;
import com.sparrowwallet.sparrow.event.TimedEvent;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.StorageException;
import com.sparrowwallet.sparrow.net.ElectrumServer;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;

public class NewWalletDialog extends DialogWindow {
    private static final Logger log = LoggerFactory.getLogger(NewWalletDialog.class);

    public NewWalletDialog(String title) {
        super(title);
    }

    protected void discoverAndSaveWallet(Wallet wallet) {
        if(AppServices.onlineProperty().get()) {
            discoverAccounts(wallet);
        } else {
            saveWallet(wallet);
        }
    }

    private void discoverAccounts(Wallet wallet) {
        DiscoveringDialog discoveringDialog = new DiscoveringDialog(wallet);
        SparrowTerminal.get().getGui().addWindow(discoveringDialog);

        Platform.runLater(() -> {
            ElectrumServer.WalletDiscoveryService walletDiscoveryService = new ElectrumServer.WalletDiscoveryService(List.of(wallet));
            walletDiscoveryService.setOnSucceeded(successEvent -> {
                SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                    SparrowTerminal.get().getGui().removeWindow(discoveringDialog);
                    saveWallet(wallet);
                });
            });
            walletDiscoveryService.setOnFailed(failedEvent -> {
                SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                    SparrowTerminal.get().getGui().removeWindow(discoveringDialog);
                    saveWallet(wallet);
                });
                log.error("Failed to discover accounts", failedEvent.getSource().getException());
            });
            walletDiscoveryService.start();
        });
    }

    private void saveWallet(Wallet wallet) {
        Storage storage = new Storage(Storage.getWalletFile(wallet.getName()));

        TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
        builder.setDescription(SettingsDialog.PasswordRequirement.UPDATE_NEW.getDescription());
        builder.setPasswordInput(true);

        String password = builder.build().showDialog(SparrowTerminal.get().getGui());
        if(password != null) {
            Platform.runLater(() -> {
                if(password.length() == 0) {
                    try {
                        storage.setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
                        storage.saveWallet(wallet);
                        storage.restorePublicKeysFromSeed(wallet, null);
                        SparrowTerminal.addWallet(storage, wallet);

                        for(Wallet childWallet : wallet.getChildWallets()) {
                            storage.saveWallet(childWallet);
                            storage.restorePublicKeysFromSeed(childWallet, null);
                            SparrowTerminal.addWallet(storage, childWallet);
                        }

                        SparrowTerminal.get().getGuiThread().invokeLater(() -> LoadWallet.getOpeningDialog(storage, wallet).showDialog(SparrowTerminal.get().getGui()));
                    } catch(IOException | StorageException | MnemonicException e) {
                        log.error("Error saving imported wallet", e);
                    }
                } else {
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(storage, new SecureString(password));
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = null;

                        try {
                            ECKey encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);
                            key = new Key(encryptionFullKey.getPrivKeyBytes(), storage.getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                            wallet.encrypt(key);
                            storage.setEncryptionPubKey(encryptionPubKey);
                            storage.saveWallet(wallet);
                            storage.restorePublicKeysFromSeed(wallet, key);
                            SparrowTerminal.addWallet(storage, wallet);

                            for(Wallet childWallet : wallet.getChildWallets()) {
                                if(!childWallet.isNested()) {
                                    childWallet.encrypt(key);
                                }
                                storage.saveWallet(childWallet);
                                storage.restorePublicKeysFromSeed(childWallet, key);
                                SparrowTerminal.addWallet(storage, childWallet);
                            }

                            SparrowTerminal.get().getGuiThread().invokeLater(() -> LoadWallet.getOpeningDialog(storage, wallet).showDialog(SparrowTerminal.get().getGui()));
                        } catch(IOException | StorageException | MnemonicException e) {
                            log.error("Error saving imported wallet", e);
                        } finally {
                            encryptionFullKey.clear();
                            if(key != null) {
                                key.clear();
                            }
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.END, "Failed"));
                        showErrorDialog("Error encrypting wallet", keyDerivationService.getException().getMessage());
                    });
                    EventManager.get().post(new StorageEvent(Storage.getWalletFile(wallet.getName()).getAbsolutePath(), TimedEvent.Action.START, "Encrypting wallet..."));
                    keyDerivationService.start();
                }
            });
        }
    }

    private static final class DiscoveringDialog extends DialogWindow {
        public DiscoveringDialog(Wallet wallet) {
            super(wallet.getName());

            setHints(List.of(Hint.CENTERED));
            setFixedSize(new TerminalSize(30, 5));

            Panel mainPanel = new Panel();
            mainPanel.setLayoutManager(new LinearLayout());
            mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

            Label label = new Label("Discovering Accounts...");
            mainPanel.addComponent(label, LinearLayout.createLayoutData(LinearLayout.Alignment.Center));

            mainPanel.addComponent(new EmptySpace(), LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning, LinearLayout.GrowPolicy.CanGrow));

            setComponent(mainPanel);
        }
    }
}
