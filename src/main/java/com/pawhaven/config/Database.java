package com.pawhaven.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class Database {
    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException exception) {
            throw new SQLException("PostgreSQL JDBC driver is missing from the application.", exception);
        }
        String url = System.getenv().getOrDefault(
                "PAWHAVEN_DB_URL", "jdbc:postgresql://localhost:5432/pawhaven");
        String user = System.getenv().getOrDefault("PAWHAVEN_DB_USER", "postgres");
        String password = System.getenv().getOrDefault("PAWHAVEN_DB_PASSWORD", "postgres");
        return DriverManager.getConnection(url, user, password);
    }
}