package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.Network;
import com.sparrowwallet.drongo.policy.Miniscript;
import com.sparrowwallet.drongo.policy.Policy;
import com.sparrowwallet.drongo.policy.PolicyType;
import com.sparrowwallet.drongo.protocol.ScriptType;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WalletMapper implements RowMapper<Wallet> {
    @Override
    public Wallet map(ResultSet rs, StatementContext ctx) throws SQLException {
        Wallet wallet = new Wallet(rs.getString("wallet.name"));
        wallet.setId(rs.getLong("wallet.id"));
        wallet.setLabel(rs.getString("wallet.label"));
        wallet.setNetwork(Network.values()[rs.getInt("wallet.network")]);
        wallet.setPolicyType(PolicyType.values()[rs.getInt("wallet.policyType")]);
        wallet.setScriptType(ScriptType.values()[rs.getInt("wallet.scriptType")]);

        Policy policy = new Policy(rs.getString("policy.name"), new Miniscript(rs.getString("policy.script")));
        policy.setId(rs.getLong("policy.id"));
        wallet.setDefaultPolicy(policy);

        int storedBlockHeight = rs.getInt("wallet.storedBlockHeight");
        wallet.setStoredBlockHeight(rs.wasNull() ? null : storedBlockHeight);

        int gapLimit = rs.getInt("wallet.gapLimit");
        wallet.gapLimit(rs.wasNull() ? null : gapLimit);
        int watchLast = rs.getInt("wallet.watchLast");
        wallet.setWatchLast(rs.wasNull() ? null : watchLast);
        wallet.setBirthDate(rs.getTimestamp("wallet.birthDate"));

        return wallet;
    }
}
