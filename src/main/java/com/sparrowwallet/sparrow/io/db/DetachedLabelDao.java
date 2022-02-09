package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.wallet.Wallet;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.*;

public interface DetachedLabelDao {
    @SqlQuery("select entry, label from detachedLabel")
    @RegisterRowMapper(DetachedLabelMapper.class)
    Map<String, String> getAll();

    @SqlBatch("insert into detachedLabel (entry, label) values (?, ?)")
    void insertDetachedLabels(List<String> entries, List<String> labels);

    @SqlUpdate("delete from detachedLabel")
    void clear();

    default void clearAndAddAll(Wallet wallet) {
        clear();

        List<String> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for(Map.Entry<String, String> labelEntry : new HashSet<>(wallet.getDetachedLabels().entrySet())) {
            entries.add(truncate(labelEntry.getKey(), 80));
            labels.add(truncate(labelEntry.getValue(), 255));
        }

        insertDetachedLabels(entries, labels);
    }

    default String truncate(String label, int length) {
        return (label != null && label.length() > length ? label.substring(0, length) : label);
    }
}
