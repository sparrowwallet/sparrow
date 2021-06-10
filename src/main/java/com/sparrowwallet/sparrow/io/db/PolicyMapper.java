package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.policy.Miniscript;
import com.sparrowwallet.drongo.policy.Policy;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PolicyMapper implements RowMapper<Policy> {
    @Override
    public Policy map(ResultSet rs, StatementContext ctx) throws SQLException {
        Policy policy = new Policy(rs.getString("name"), new Miniscript(rs.getString("script")));
        policy.setId(rs.getLong("id"));
        return policy;
    }
}
