package com.terrabox.database;

import java.sql.*;

public interface DatabaseProvider {
    void init() throws SQLException;
    void close() throws SQLException;
    boolean isValid();
    String getType();
    Connection getConnection() throws SQLException;
    default ResultSet query(String sql, Object... params) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps.executeQuery();
    }
    default int update(String sql, Object... params) throws SQLException {
        PreparedStatement ps = getConnection().prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps.executeUpdate();
    }
}
