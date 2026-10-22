package com.minibanca.core.service;

/** Los niveles que admite PostgreSQL (READ UNCOMMITTED lo trata igual que READ COMMITTED). */
public enum NivelAislamiento {
    READ_COMMITTED("READ COMMITTED"),
    REPEATABLE_READ("REPEATABLE READ"),
    SERIALIZABLE("SERIALIZABLE");

    private final String sql;

    NivelAislamiento(String sql) {
        this.sql = sql;
    }

    public String sql() {
        return sql;
    }
}
