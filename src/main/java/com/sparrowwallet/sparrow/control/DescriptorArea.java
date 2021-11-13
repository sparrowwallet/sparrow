package com.sparrowwallet.sparrow.control;

import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.OutputDescriptor;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.Wallet;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.fxmisc.richtext.CodeArea;

import java.util.List;

import static com.sparrowwallet.drongo.policy.PolicyType.MULTI;
import static com.sparrowwallet.drongo.policy.PolicyType.SINGLE;
import static com.sparrowwallet.drongo.protocol.ScriptType.MULTISIG;

public class DescriptorArea extends CodeArea {
    private Wallet wallet;

    public void setWallet(Wallet wallet) {
        clear();
        this.wallet = wallet;

        DescriptorContextMenu contextMenu = new DescriptorContextMenu(wallet, this);
        setContextMenu(contextMenu);

        PolicyType policyType = wallet.getPolicyType();
        ScriptType scriptType = wallet.getScriptType();
        List<Keystore> keystores = wallet.getKeystores();
        int threshold = wallet.getDefaultPolicy().getNumSignaturesRequired();

        if(SINGLE.equals(policyType)) {
            append(scriptType.getDescriptor(), "descriptor-text");
            replace(getLength(), getLength(), keystores.get(0).getScriptName(), List.of(keystores.get(0).isValid() ? "descriptor-text" : "descriptor-error", keystores.get(0).getScriptName()));
            append(scriptType.getCloseDescriptor(), "descriptor-text");
        }

        if(MULTI.equals(policyType)) {
            append(scriptType.getDescriptor(), "descriptor-text");
            append(MULTISIG.getDescriptor(), "descriptor-text");
            append(Integer.toString(threshold), "descriptor-text");

            for(Keystore keystore : keystores) {
                append(",", "descriptor-text");
                replace(getLength(), getLength(), keystore.getScriptName(), List.of(keystore.isValid() ? "descriptor-text" : "descriptor-error", keystore.getScriptName()));
            }

            append(MULTISIG.getCloseDescriptor(), "descriptor-text");
            append(scriptType.getCloseDescriptor(), "descriptor-text");
        }
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void clear() {
        super.clear();
        this.wallet = null;
        setDisable(false);
        setContextMenu(null);
    }

    private static class DescriptorContextMenu extends ContextMenu {
        public DescriptorContextMenu(Wallet wallet, DescriptorArea descriptorArea) {
            MenuItem copyvalue = new MenuItem("Copy Value");
            copyvalue.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(descriptorArea.getText());
                Clipboard.getSystemClipboard().setContent(content);
            });
            getItems().add(copyvalue);

            MenuItem copyOutputDescriptor = new MenuItem("Copy Output Descriptor");
            copyOutputDescriptor.setOnAction(AE -> {
                hide();
                ClipboardContent content = new ClipboardContent();
                content.putString(OutputDescriptor.getOutputDescriptor(wallet, KeyPurpose.DEFAULT_PURPOSES, null).toString(true));
                Clipboard.getSystemClipboard().setContent(content);
            });
            getItems().add(copyOutputDescriptor);
            this.setStyle("-fx-background-color: -fx-color; -fx-font-family: System; -fx-font-size: 1em;");
        }
    }
}
