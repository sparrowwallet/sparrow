package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.ExtendedKey;
import com.sparrowwallet.drongo.KeyDerivation;
import com.sparrowwallet.drongo.crypto.EncryptedData;
import com.sparrowwallet.drongo.crypto.EncryptionType;
import com.sparrowwallet.drongo.wallet.*;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class KeystoreMapper implements RowMapper<Keystore> {

    @Override
    public Keystore map(ResultSet rs, StatementContext ctx) throws SQLException {
        Keystore keystore = new Keystore(rs.getString("keystore.label"));
        keystore.setId(rs.getLong("keystore.id"));
        keystore.setSource(KeystoreSource.values()[rs.getInt("keystore.source")]);
        keystore.setWalletModel(WalletModel.values()[rs.getInt("keystore.walletModel")]);
        keystore.setKeyDerivation(new KeyDerivation(rs.getString("keystore.masterFingerprint"), rs.getString("keystore.derivationPath")));
        keystore.setExtendedPublicKey(rs.getString("keystore.extendedPublicKey") == null ? null : ExtendedKey.fromDescriptor(rs.getString("keystore.extendedPublicKey")));

        if(rs.getBytes("masterPrivateExtendedKey.privateKey") != null) {
            MasterPrivateExtendedKey masterPrivateExtendedKey = new MasterPrivateExtendedKey(rs.getBytes("masterPrivateExtendedKey.privateKey"), rs.getBytes("masterPrivateExtendedKey.chainCode"));
            masterPrivateExtendedKey.setId(rs.getLong("masterPrivateExtendedKey.id"));
            keystore.setMasterPrivateExtendedKey(masterPrivateExtendedKey);
        } else if(rs.getBytes("masterPrivateExtendedKey.encryptedBytes") != null) {
            EncryptedData encryptedData = new EncryptedData(rs.getBytes("masterPrivateExtendedKey.initialisationVector"),
                    rs.getBytes("masterPrivateExtendedKey.encryptedBytes"), rs.getBytes("masterPrivateExtendedKey.keySalt"),
                    EncryptionType.Deriver.values()[rs.getInt("masterPrivateExtendedKey.deriver")],
                    EncryptionType.Crypter.values()[rs.getInt("masterPrivateExtendedKey.crypter")]);
            MasterPrivateExtendedKey masterPrivateExtendedKey = new MasterPrivateExtendedKey(encryptedData);
            masterPrivateExtendedKey.setId(rs.getLong("masterPrivateExtendedKey.id"));
            keystore.setMasterPrivateExtendedKey(masterPrivateExtendedKey);
        }

        if(rs.getString("seed.mnemonicString") != null) {
            List<String> mnemonicCode = Arrays.asList(rs.getString("seed.mnemonicString").split(" "));
            DeterministicSeed seed = new DeterministicSeed(mnemonicCode, rs.getBoolean("seed.needsPassphrase"), rs.getLong("seed.creationTimeSeconds"), DeterministicSeed.Type.values()[rs.getInt("seed.type")]);
            seed.setId(rs.getLong("seed.id"));
            keystore.setSeed(seed);
        } else if(rs.getBytes("seed.encryptedBytes") != null) {
            EncryptedData encryptedData = new EncryptedData(rs.getBytes("seed.initialisationVector"),
                    rs.getBytes("seed.encryptedBytes"), rs.getBytes("seed.keySalt"),
                    EncryptionType.Deriver.values()[rs.getInt("seed.deriver")],
                    EncryptionType.Crypter.values()[rs.getInt("seed.crypter")]);
            DeterministicSeed seed = new DeterministicSeed(encryptedData, rs.getBoolean("seed.needsPassphrase"), rs.getLong("seed.creationTimeSeconds"), DeterministicSeed.Type.values()[rs.getInt("seed.type")]);
            seed.setId(rs.getLong("seed.id"));
            keystore.setSeed(seed);
        }

        return keystore;
    }
}
