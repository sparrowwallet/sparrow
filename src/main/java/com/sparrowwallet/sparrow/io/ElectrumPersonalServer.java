package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyPurpose;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

public class ElectrumPersonalServer implements WalletExport {
    private static final Logger log = LoggerFactory.getLogger(ElectrumPersonalServer.class);

    @Override
    public String getName() {
        return "Electrum Personal Server";
    }

    @Override
    public WalletModel getWalletModel() {
        return WalletModel.EPS;
    }

    @Override
    public void exportWallet(Wallet wallet, OutputStream outputStream, String password) throws ExportException {
        if(wallet.getScriptType() == ScriptType.P2TR) {
            throw new ExportException(getName() + " does not support Taproot wallets.");
        }

        try {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
            writer.write("# Electrum Personal Server configuration file fragments\n");
            writer.write("# Copy the lines below into the relevant sections in your EPS config.ini file\n\n");
            writer.write("# Copy into [master-public-keys] section\n");
            Wallet masterWallet = wallet.isMasterWallet() ? wallet : wallet.getMasterWallet();
            writeWalletXpub(masterWallet, writer);
            for(Wallet childWallet : masterWallet.getChildWallets()) {
                if(!childWallet.isNested()) {
                    writeWalletXpub(childWallet, writer);
                }
            }

            writeBip47Addresses(masterWallet, writer);

            writer.flush();
        } catch(Exception e) {
            log.error("Could not export EPS wallet", e);
            throw new ExportException("Could not export EPS wallet", e);
        }
    }

    private static void writeWalletXpub(Wallet wallet, BufferedWriter writer) throws IOException {
        writer.write(wallet.getFullName().replace(' ', '_') + " = ");

        ExtendedKey.Header xpubHeader = ExtendedKey.Header.fromScriptType(wallet.getScriptType(), false);
        if(wallet.getPolicyType() == PolicyType.MULTI) {
            writer.write(wallet.getDefaultPolicy().getNumSignaturesRequired() + " ");
        }

        for(Iterator<Keystore> iter = wallet.getKeystores().iterator(); iter.hasNext(); ) {
            Keystore keystore = iter.next();
            writer.write(keystore.getExtendedPublicKey().toString(xpubHeader));

            if(iter.hasNext()) {
                writer.write(" ");
            }
        }

        writer.newLine();
    }

    private static void writeBip47Addresses(Wallet wallet, BufferedWriter writer) throws IOException {
        if(wallet.hasPaymentCode()) {
            writer.write("\n# Copy into [watch-only-addresses] section\n");
            WalletNode notificationNode = wallet.getNotificationWallet().getNode(KeyPurpose.NOTIFICATION);
            writer.write(wallet.getFullName().replace(' ', '_') + "-notification_addr = " + notificationNode.getAddress().toString() + "\n");

            for(Wallet childWallet : wallet.getChildWallets()) {
                if(childWallet.isBip47()) {
                    writer.write(childWallet.getFullName().replace(' ', '_') + " = ");
                    for(Iterator<KeyPurpose> purposeIterator = KeyPurpose.DEFAULT_PURPOSES.iterator(); purposeIterator.hasNext(); ) {
                        KeyPurpose keyPurpose = purposeIterator.next();
                        for(Iterator<WalletNode> iter = childWallet.getNode(keyPurpose).getChildren().iterator(); iter.hasNext(); ) {
                            WalletNode receiveNode = iter.next();
                            writer.write(receiveNode.getAddress().toString());

                            if(iter.hasNext()) {
                                writer.write(" ");
                            }
                        }

                        if(purposeIterator.hasNext()) {
                            writer.write(" ");
                        }
                    }

                    writer.newLine();
                }
            }

            writer.write("\n# Important: If this wallet receives any BIP47 payments, redo this export");
        }
    }

    @Override
    public String getWalletExportDescription() {
        return "Export this wallet as a configuration file fragment to copy into your Electrum Personal Server (EPS) config.ini file.";
    }

    @Override
    public String getExportFileExtension(Wallet wallet) {
        return "ini";
    }

    @Override
    public boolean isWalletExportScannable() {
        return false;
    }

    @Override
    public boolean walletExportRequiresDecryption() {
        return false;
    }

    @Override
    public boolean exportsAllWallets() {
        return true;
    }
}
