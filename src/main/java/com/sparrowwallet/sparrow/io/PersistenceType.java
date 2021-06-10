package com.sparrowwallet.sparrow.io;

import com.sparrowwallet.sparrow.io.db.DbPersistence;

public enum PersistenceType {
    JSON("json") {
        @Override
        public String getExtension() {
            return getName();
        }

        @Override
        public Persistence getInstance() {
            return new JsonPersistence();
        }
    },
    DB("db") {
        @Override
        public String getExtension() {
            return "mv.db";
        }

        @Override
        public Persistence getInstance() {
            return new DbPersistence();
        }
    };

    private final String name;

    private PersistenceType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract String getExtension();

    public abstract Persistence getInstance();
}
