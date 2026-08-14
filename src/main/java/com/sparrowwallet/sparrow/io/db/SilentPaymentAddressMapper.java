package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.address.P2TRAddress;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class SilentPaymentAddressMapper implements RowMapper<Map.Entry<Address, SilentPaymentAddress>> {
    @Override
    public Map.Entry<Address, SilentPaymentAddress> map(ResultSet rs, StatementContext ctx) throws SQLException {
        Address address = new P2TRAddress(rs.getBytes("address"));
        SilentPaymentAddress silentPaymentAddress = SilentPaymentAddress.fromBytes(rs.getBytes("silentPaymentAddress"));

        return new Map.Entry<>() {
            @Override
            public Address getKey() {
                return address;
            }

            @Override
            public SilentPaymentAddress getValue() {
                return silentPaymentAddress;
            }

            @Override
            public SilentPaymentAddress setValue(SilentPaymentAddress value) {
                return null;
            }
        };
    }
}
