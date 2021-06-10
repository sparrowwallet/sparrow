package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.crypto.EncryptedData;
import com.sparrowwallet.drongo.wallet.DeterministicSeed;
import com.sparrowwallet.drongo.wallet.Keystore;
import com.sparrowwallet.drongo.wallet.MasterPrivateExtendedKey;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;

public interface KeystoreDao {
    @SqlQuery("select keystore.id, keystore.label, keystore.source, keystore.walletModel, keystore.masterFingerprint, keystore.derivationPath, keystore.extendedPublicKey, " +
              "masterPrivateExtendedKey.id, masterPrivateExtendedKey.privateKey, masterPrivateExtendedKey.chainCode, masterPrivateExtendedKey.initialisationVector, masterPrivateExtendedKey.encryptedBytes, masterPrivateExtendedKey.keySalt, masterPrivateExtendedKey.deriver, masterPrivateExtendedKey.crypter, " +
              "seed.id, seed.type, seed.mnemonicString, seed.initialisationVector, seed.encryptedBytes, seed.keySalt, seed.deriver, seed.crypter, seed.needsPassphrase, seed.creationTimeSeconds " +
              "from keystore left join masterPrivateExtendedKey on keystore.masterPrivateExtendedKey = masterPrivateExtendedKey.id left join seed on keystore.seed = seed.id where keystore.wallet = ? order by keystore.index asc")
    @RegisterRowMapper(KeystoreMapper.class)
    List<Keystore> getForWalletId(Long id);

    @SqlUpdate("insert into keystore (label, source, walletModel, masterFingerprint, derivationPath, extendedPublicKey, masterPrivateExtendedKey, seed, wallet, index) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insert(String label, int source, int walletModel, String masterFingerprint, String derivationPath, String extendedPublicKey, Long masterPrivateExtendedKey, Long seed, long wallet, int index);

    @SqlUpdate("insert into masterPrivateExtendedKey (privateKey, chainCode, initialisationVector, encryptedBytes, keySalt, deriver, crypter, creationTimeSeconds) values (?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertMasterPrivateExtendedKey(byte[] privateKey, byte[] chainCode, byte[] initialisationVector, byte[] encryptedBytes, byte[] keySalt, Integer deriver, Integer crypter, long creationTimeSeconds);

    @SqlUpdate("insert into seed (type, mnemonicString, initialisationVector, encryptedBytes, keySalt, deriver, crypter, needsPassphrase, creationTimeSeconds) values (?, ?, ?, ?, ?, ?, ?, ?, ?)")
    @GetGeneratedKeys("id")
    long insertSeed(int type, String mnemonicString, byte[] initialisationVector, byte[] encryptedBytes, byte[] keySalt, Integer deriver, Integer crypter, boolean needsPassphrase, long creationTimeSeconds);

    @SqlUpdate("update keystore set label = ? where id = ?")
    void updateLabel(String label, long id);

    default void addKeystores(Wallet wallet) {
        for(int i = 0; i < wallet.getKeystores().size(); i++) {
            Keystore keystore = wallet.getKeystores().get(i);
            if(keystore.getMasterPrivateExtendedKey() != null) {
                MasterPrivateExtendedKey mpek = keystore.getMasterPrivateExtendedKey();
                if(mpek.isEncrypted()) {
                    EncryptedData data = mpek.getEncryptedData();
                    long id = insertMasterPrivateExtendedKey(null, null, data.getInitialisationVector(), data.getEncryptedBytes(), data.getKeySalt(), data.getEncryptionType().getDeriver().ordinal(), data.getEncryptionType().getCrypter().ordinal(), mpek.getCreationTimeSeconds());
                    mpek.setId(id);
                } else {
                    long id = insertMasterPrivateExtendedKey(mpek.getPrivateKey().getPrivKeyBytes(), mpek.getPrivateKey().getChainCode(), null, null, null, null, null, mpek.getCreationTimeSeconds());
                    mpek.setId(id);
                }
            }

            if(keystore.getSeed() != null) {
                DeterministicSeed seed = keystore.getSeed();
                if(seed.isEncrypted()) {
                    EncryptedData data = seed.getEncryptedData();
                    long id = insertSeed(seed.getType().ordinal(), null, data.getInitialisationVector(), data.getEncryptedBytes(), data.getKeySalt(), data.getEncryptionType().getDeriver().ordinal(), data.getEncryptionType().getCrypter().ordinal(), seed.needsPassphrase(), seed.getCreationTimeSeconds());
                    seed.setId(id);
                } else {
                    long id = insertSeed(seed.getType().ordinal(), seed.getMnemonicString().asString(), null, null, null, null, null, seed.needsPassphrase(), seed.getCreationTimeSeconds());
                    seed.setId(id);
                }
            }

            long id = insert(keystore.getLabel(), keystore.getSource().ordinal(), keystore.getWalletModel().ordinal(),
                    keystore.hasPrivateKey() ? null : keystore.getKeyDerivation().getMasterFingerprint(),
                    keystore.getKeyDerivation().getDerivationPath(),
                    keystore.hasPrivateKey() ? null : keystore.getExtendedPublicKey().toString(),
                    keystore.getMasterPrivateExtendedKey() == null ? null : keystore.getMasterPrivateExtendedKey().getId(),
                    keystore.getSeed() == null ? null : keystore.getSeed().getId(), wallet.getId(), i);
            keystore.setId(id);
        }
    }
}
