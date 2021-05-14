package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.AsymmetricKeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public interface Persistence {
    Wallet loadWallet(File walletFile) throws IOException;
    WalletBackupAndKey loadWallet(File walletFile, CharSequence password) throws IOException, StorageException;
    Map<File, Wallet> loadWallets(File[] walletFiles, ECKey encryptionKey) throws IOException, StorageException;
    Map<Storage, WalletBackupAndKey> loadChildWallets(File walletFile, Wallet masterWallet, ECKey encryptionKey) throws IOException, StorageException;
    File storeWallet(File walletFile, Wallet wallet) throws IOException;
    File storeWallet(File walletFile, Wallet wallet, ECKey encryptionPubKey) throws IOException;
    ECKey getEncryptionKey(CharSequence password) throws IOException, StorageException;
    AsymmetricKeyDeriver getKeyDeriver();
    void setKeyDeriver(AsymmetricKeyDeriver keyDeriver);
    PersistenceType getType();
}
