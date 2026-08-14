package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.address.Address;
import com.sparrowwallet.drongo.silentpayments.SilentPaymentAddress;
import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface SilentPaymentAddressDao {
    @SqlQuery("select address, silentPaymentAddress from silentPaymentAddress")
    @RegisterRowMapper(SilentPaymentAddressMapper.class)
    Map<Address, SilentPaymentAddress> getAll();

    @SqlBatch("insert into silentPaymentAddress (address, silentPaymentAddress) values (?, ?)")
    void insertSilentPaymentAddresses(List<byte[]> addresses, List<byte[]> silentPaymentAddresses);

    @SqlUpdate("delete from silentPaymentAddress")
    void clear();

    default void clearAndAddAll(Wallet wallet) {
        clear();

        List<byte[]> addresses = new ArrayList<>();
        List<byte[]> silentPaymentAddresses = new ArrayList<>();
        for(Map.Entry<Address, SilentPaymentAddress> entry : wallet.getSilentPaymentAddresses().entrySet()) {
            addresses.add(entry.getKey().getData());
            silentPaymentAddresses.add(entry.getValue().serialize());
        }

        if(!addresses.isEmpty()) {
            insertSilentPaymentAddresses(addresses, silentPaymentAddresses);
        }
    }
}
