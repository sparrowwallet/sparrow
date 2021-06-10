package com.sparrowwallet.sparrow.io.db;

import com.sparrowwallet.drongo.policy.Policy;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface PolicyDao {
    @SqlQuery("select * from policy where id = ?")
    @RegisterRowMapper(PolicyMapper.class)
    Policy getPolicy(long id);

    @SqlUpdate("insert into policy (name, script) values (?, ?)")
    @GetGeneratedKeys("id")
    long insert(String name, String script);

    default void addPolicy(Policy policy) {
        long id = insert(policy.getName(), policy.getMiniscript().getScript());
        policy.setId(id);
    }
}
