package com.sparrowwallet.sparrow.terminal.wallet;

import com.google.common.eventbus.Subscribe;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.dialogs.TextInputDialogBuilder;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.SecureString;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.crypto.InvalidPasswordException;
import com.sparrowwallet.drongo.crypto.Key;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.wallet.KeystoreSource;
import com.sparrowwallet.drongo.wallet.StandardAccount;
import com.sparrowwallet.drongo.wallet.Wallet;
import com.sparrowwallet.sparrow.AppServices;
import com.sparrowwallet.sparrow.EventManager;
import com.sparrowwallet.sparrow.event.*;
import com.sparrowwallet.sparrow.io.Storage;
import com.sparrowwallet.sparrow.io.StorageException;
import com.sparrowwallet.sparrow.terminal.SparrowTerminal;
import com.sparrowwallet.sparrow.wallet.Function;
import com.sparrowwallet.sparrow.wallet.SettingsWalletForm;
import com.sparrowwallet.sparrow.wallet.WalletForm;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static com.sparrowwallet.sparrow.AppServices.showErrorDialog;
import static com.sparrowwallet.sparrow.AppServices.showSuccessDialog;

public class SettingsDialog extends WalletDialog {
    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private final Label scriptType;
    private final TextBox outputDescriptor;

    public SettingsDialog(WalletForm walletForm) {
        super(walletForm.getWallet().getFullDisplayName() + " Settings", walletForm);

        setHints(List.of(Hint.CENTERED));

        Panel mainPanel = new Panel(new GridLayout(2).setHorizontalSpacing(2).setVerticalSpacing(0).setTopMarginSize(1));

        mainPanel.addComponent(new Label("Script Type"));
        scriptType = new Label(getWalletForm().getWallet().getScriptType().getDescription()).addTo(mainPanel);

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        TerminalSize screenSize = SparrowTerminal.get().getScreen().getTerminalSize();
        int descriptorWidth = Math.min(Math.max(20, screenSize.getColumns() - 20), 120);

        OutputDescriptor descriptor = OutputDescriptor.getOutputDescriptor(getWalletForm().getWallet(), KeyPurpose.DEFAULT_PURPOSES, null);
        String outputDescriptorString = descriptor.toString(true);
        List<String> outputDescriptorLines = splitString(outputDescriptorString, descriptorWidth);

        mainPanel.addComponent(new Label("Output Descriptor"));
        outputDescriptor = new TextBox(new TerminalSize(descriptorWidth, Math.min(outputDescriptorLines.size(), 10)));
        outputDescriptor.setReadOnly(true);
        outputDescriptor.setText(outputDescriptorLines.stream().reduce((s1, s2) -> s1 + "\n" + s2).get());
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        mainPanel.addComponent(outputDescriptor, GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER, true, true, 2, 1));

        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));

        if(getWalletForm().getMasterWallet().getKeystores().stream().allMatch(ks -> ks.getSource() == KeystoreSource.SW_SEED)) {
            Panel leftButtonPanel = new Panel();
            leftButtonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
            leftButtonPanel.addComponent(new Button("Add Account", this::showAddAccount));
            if(getWalletForm().getWallet().getPolicyType() == PolicyType.SINGLE) {
                leftButtonPanel.addComponent(new Button("Show Seed", this::showSeed));
            } else {
                leftButtonPanel.addComponent(new EmptySpace(TerminalSize.ZERO));
            }

            leftButtonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.BEGINNING, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        } else {
            mainPanel.addComponent(new EmptySpace(TerminalSize.ONE));
        }

        Panel rightButtonPanel = new Panel();
        rightButtonPanel.setLayoutManager(new GridLayout(2).setHorizontalSpacing(1));
        rightButtonPanel.addComponent(new Button("Back", () -> onBack(Function.SETTINGS)));
        rightButtonPanel.addComponent(new Button("Advanced", this::showAdvanced).setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.CENTER, GridLayout.Alignment.CENTER, true, false)));

        rightButtonPanel.setLayoutData(GridLayout.createLayoutData(GridLayout.Alignment.END, GridLayout.Alignment.CENTER,false,false)).addTo(mainPanel);
        setComponent(mainPanel);
    }

    private void showAdvanced() {
        AdvancedDialog advancedDialog = new AdvancedDialog(getWalletForm());
        AdvancedDialog.Result result = (AdvancedDialog.Result)advancedDialog.showDialog(SparrowTerminal.get().getGui());

        if(result != AdvancedDialog.Result.CANCEL) {
            saveWallet(false, result == AdvancedDialog.Result.CHANGE_PASSWORD);
        }
    }

    private void showAddAccount() {
        Wallet openWallet = AppServices.get().getOpenWallets().entrySet().stream().filter(entry -> getWalletForm().getWalletFile().equals(entry.getValue().getWalletFile())).map(Map.Entry::getKey).findFirst().orElseThrow();
        Wallet masterWallet = openWallet.isMasterWallet() ? openWallet : openWallet.getMasterWallet();

        AddAccountDialog addAccountDialog = new AddAccountDialog(masterWallet);
        StandardAccount standardAccount = addAccountDialog.showDialog(SparrowTerminal.get().getGui());

        if(standardAccount != null) {
            addAccount(masterWallet, standardAccount, () -> {
                SparrowTerminal.get().getGuiThread().invokeLater(() -> {
                    if(StandardAccount.WHIRLPOOL_ACCOUNTS.contains(standardAccount)) {
                        showSuccessDialog("Added Accounts", "Whirlpool Accounts have been successfully added.");
                    } else {
                        showSuccessDialog("Added Account", standardAccount.getName() + " has been successfully added.");
                    }
                });
            });
        }
    }

    private void showSeed() {
        Wallet wallet = getWalletForm().getWallet().copy();
        if(wallet.isEncrypted()) {
            Wallet copy = wallet.copy();
            String walletId = getWalletForm().getWalletId();

            TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
            builder.setDescription("Enter the wallet password:");
            builder.setPasswordInput(true);

            String password = builder.build().showDialog(SparrowTerminal.get().getGui());
            if(password != null) {
                Platform.runLater(() -> {
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(getWalletForm().getStorage(), new SecureString(password), true);
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletId, TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = null;

                        try {
                            key = new Key(encryptionFullKey.getPrivKeyBytes(), getWalletForm().getStorage().getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);
                            copy.decrypt(key);
                            showSuccessDialog("Wallet Seed", copy.getKeystores().get(0).getSeed().getMnemonicString().asString());
                        } finally {
                            encryptionFullKey.clear();
                            if(key != null) {
                                key.clear();
                            }
                        }
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
            showSuccessDialog("Wallet Seed", wallet.getKeystores().get(0).getSeed().getMnemonicString().asString());
        }
    }

    private void saveWallet(boolean changePassword, boolean suggestChangePassword) {
        WalletForm walletForm = getWalletForm();
        ECKey existingPubKey = walletForm.getStorage().getEncryptionPubKey();

        PasswordRequirement requirement;
        if(existingPubKey == null) {
            if(changePassword) {
                requirement = PasswordRequirement.UPDATE_CHANGE;
            } else {
                requirement = PasswordRequirement.UPDATE_NEW;
            }
        } else if(Storage.NO_PASSWORD_KEY.equals(existingPubKey)) {
            requirement = PasswordRequirement.UPDATE_EMPTY;
        } else {
            requirement = PasswordRequirement.UPDATE_SET;
        }

        TextInputDialogBuilder builder = new TextInputDialogBuilder().setTitle("Wallet Password");
        builder.setDescription(requirement.description);
        builder.setPasswordInput(true);

        String password = builder.build().showDialog(SparrowTerminal.get().getGui());
        if(password != null) {
            Platform.runLater(() -> {
                if(password.length() == 0 && requirement != PasswordRequirement.UPDATE_SET) {
                    try {
                        walletForm.getStorage().setEncryptionPubKey(Storage.NO_PASSWORD_KEY);
                        walletForm.saveAndRefresh();
                        EventManager.get().post(new RequestOpenWalletsEvent());
                    } catch (IOException | StorageException e) {
                        log.error("Error saving wallet", e);
                        AppServices.showErrorDialog("Error saving wallet", e.getMessage());
                    }
                } else {
                    Storage.KeyDerivationService keyDerivationService = new Storage.KeyDerivationService(walletForm.getStorage(), new SecureString(password));
                    keyDerivationService.setOnSucceeded(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletForm.getWalletId(), TimedEvent.Action.END, "Done"));
                        ECKey encryptionFullKey = keyDerivationService.getValue();
                        Key key = null;

                        try {
                            ECKey encryptionPubKey = ECKey.fromPublicOnly(encryptionFullKey);

                            if(existingPubKey != null && !Storage.NO_PASSWORD_KEY.equals(existingPubKey) && !existingPubKey.equals(encryptionPubKey)) {
                                AppServices.showErrorDialog("Incorrect Password", "The password was incorrect.");
                                return;
                            }

                            key = new Key(encryptionFullKey.getPrivKeyBytes(), walletForm.getStorage().getKeyDeriver().getSalt(), EncryptionType.Deriver.ARGON2);

                            Wallet masterWallet = walletForm.getWallet().isMasterWallet() ? walletForm.getWallet() : walletForm.getWallet().getMasterWallet();
                            if(suggestChangePassword && requirement == PasswordRequirement.UPDATE_SET) {
                                walletForm.getStorage().setEncryptionPubKey(null);
                                masterWallet.decrypt(key);
                                for(Wallet childWallet : masterWallet.getChildWallets()) {
                                    if(!childWallet.isNested()) {
                                        childWallet.decrypt(key);
                                    }
                                }
                                SparrowTerminal.get().getGuiThread().invokeLater(() -> saveWallet(true, false));
                                return;
                            }

                            masterWallet.encrypt(key);
                            for(Wallet childWallet : masterWallet.getChildWallets()) {
                                if(!childWallet.isNested()) {
                                    childWallet.encrypt(key);
                                }
                            }
                            walletForm.getStorage().setEncryptionPubKey(encryptionPubKey);
                            walletForm.saveAndRefresh();
                            EventManager.get().post(new RequestOpenWalletsEvent());
                        } catch (Exception e) {
                            log.error("Error saving wallet", e);
                            AppServices.showErrorDialog("Error saving wallet", e.getMessage());
                        } finally {
                            encryptionFullKey.clear();
                            if(key != null) {
                                key.clear();
                            }
                        }
                    });
                    keyDerivationService.setOnFailed(workerStateEvent -> {
                        EventManager.get().post(new StorageEvent(walletForm.getWalletId(), TimedEvent.Action.END, "Failed"));
                        AppServices.showErrorDialog("Error saving wallet", keyDerivationService.getException().getMessage());
                    });
                    EventManager.get().post(new StorageEvent(walletForm.getWalletId(), TimedEvent.Action.START, "Encrypting wallet..."));
                    keyDerivationService.start();
                }
            });
        }
    }

    public static List<String> splitString(String stringToSplit, int maxLength) {
        String text = stringToSplit;
        List<String> lines = new ArrayList<>();
        while(text.length() >= maxLength) {
            int breakAt = maxLength - 1;
            lines.add(text.substring(0, breakAt));
            text = text.substring(breakAt);
        }

        lines.add(text);
        return lines;
    }

    @Subscribe
    public void newChildWalletSaved(NewChildWalletSavedEvent event) {
        if(event.getMasterWalletId().equals(getWalletForm().getMasterWalletId())) {
            ((SettingsWalletForm)getWalletForm()).childWalletSaved(event.getChildWallet());
        }
    }

    public enum PasswordRequirement {
        UPDATE_NEW("Add a password to the wallet?\nLeave empty for no password:", "No Password"),
        UPDATE_EMPTY("This wallet has no password.\nAdd a password to the wallet?\nLeave empty for no password:", "No Password"),
        UPDATE_SET("Re-enter the wallet password:", "Verify Password"),
        UPDATE_CHANGE("Enter the new wallet password.\nLeave empty for no password:", "No Password");

        private final String description;
        private final String okButtonText;

        PasswordRequirement(String description, String okButtonText) {
            this.description = description;
            this.okButtonText = okButtonText;
        }

        public String getDescription() {
            return description;
        }
    }
}
