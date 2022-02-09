package com.sparrowwallet.sparrow.io.db;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class DetachedLabelMapper implements RowMapper<Map.Entry<String, String>> {
    @Override
    public Map.Entry<String, String> map(ResultSet rs, StatementContext ctx) throws SQLException {
        String entry = rs.getString("entry");
        String label = rs.getString("label");

        return new Map.Entry<>() {
            @Override
            public String getKey() {
                return entry;
            }

            @Override
            public String getValue() {
                return label;
            }

            @Override
            public String setValue(String value) {
                return null;
            }
        };
    }
}
