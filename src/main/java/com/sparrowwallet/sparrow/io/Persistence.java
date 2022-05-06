package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.drongo.crypto.AsymmetricKeyDeriver;
import com.sparrowwallet.drongo.crypto.ECKey;
import com.sparrowwallet.drongo.wallet.Wallet;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

public interface Persistence {
    WalletAndKey loadWallet(Storage storage) throws IOException, StorageException;
    WalletAndKey loadWallet(Storage storage, CharSequence password) throws IOException, StorageException;
    WalletAndKey loadWallet(Storage storage, CharSequence password, ECKey alreadyDerivedKey) throws IOException, StorageException;
    File storeWallet(Storage storage, Wallet wallet) throws IOException, StorageException;
    File storeWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws IOException, StorageException;
    void updateWallet(Storage storage, Wallet wallet) throws IOException, StorageException;
    void updateWallet(Storage storage, Wallet wallet, ECKey encryptionPubKey) throws IOException, StorageException;
    boolean isPersisted(Storage storage, Wallet wallet);
    ECKey getEncryptionKey(CharSequence password) throws IOException, StorageException;
    AsymmetricKeyDeriver getKeyDeriver();
    void setKeyDeriver(AsymmetricKeyDeriver keyDeriver);
    PersistenceType getType();
    boolean isEncrypted(File walletFile) throws IOException;
    String getWalletId(Storage storage, Wallet wallet);
    String getWalletName(File walletFile, Wallet wallet);
    void copyWallet(File walletFile, OutputStream outputStream) throws IOException;
    boolean isClosed();
    void close();
}
