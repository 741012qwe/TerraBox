package com.terrabox.database;

import java.sql.ResultSet;
import java.sql.SQLException;

public interface DatabaseProvider {
    void init() throws SQLException;
    void close() throws SQLException;
    boolean isValid();
    String getType();
    ResultSet query(String sql) throws SQLException;
    int update(String sql) throws SQLException;
}
