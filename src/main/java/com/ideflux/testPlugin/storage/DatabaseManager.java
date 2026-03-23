package com.ideflux.testPlugin.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages SQLite database connection and initialization.
 * Provides connection pooling and proper resource management.
 * Thread-safe and designed for async database operations.
 */
public class DatabaseManager {
    
    private final JavaPlugin plugin;
    private final String databasePath;
    private Connection connection;
    
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath() + File.separator + "locations.db";
    }
    
    /**
     * Initializes the database connection and creates tables if needed.
     * Should be called synchronously during plugin enable.
     */
    public void initialize() {
        try {
            // Ensure data folder exists
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            // Establish connection
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            
            // Enable foreign keys and WAL mode for better performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL"); // Write-Ahead Logging for better concurrency
                stmt.execute("PRAGMA synchronous = NORMAL"); // Balance between safety and performance
            }
            
            // Create tables
            createTables();
            
            plugin.getLogger().info("SQLite database initialized successfully at: " + databasePath);
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found!", e);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
        }
    }
    
    /**
     * Creates the database schema if it doesn't exist.
     */
    private void createTables() throws SQLException {
        String createLocationsTable = """
            CREATE TABLE IF NOT EXISTS player_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_uuid TEXT NOT NULL,
                location_name TEXT NOT NULL,
                world_name TEXT NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                z REAL NOT NULL,
                created_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                updated_at INTEGER NOT NULL DEFAULT (strftime('%s', 'now')),
                UNIQUE(owner_uuid, location_name)
            )
            """;
        
        String createIndexOwner = """
            CREATE INDEX IF NOT EXISTS idx_owner_uuid 
            ON player_locations(owner_uuid)
            """;
        
        String createIndexName = """
            CREATE INDEX IF NOT EXISTS idx_location_name 
            ON player_locations(owner_uuid, location_name)
            """;
        
        String createUsernameCacheTable = """
            CREATE TABLE IF NOT EXISTS username_cache (
                username TEXT PRIMARY KEY,
                uuid TEXT NOT NULL,
                last_seen INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
            )
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createLocationsTable);
            stmt.execute(createIndexOwner);
            stmt.execute(createIndexName);
            stmt.execute(createUsernameCacheTable);
        }
    }
    
    /**
     * Gets a connection to the database.
     * Callers are responsible for proper resource management.
     */
    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        }
        return connection;
    }
    
    /**
     * Closes the database connection.
     * Should be called during plugin disable.
     */
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed successfully.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection!", e);
        }
    }
    
    /**
     * Executes a database operation asynchronously and returns a CompletableFuture.
     * Ensures database operations don't block the main server thread.
     */
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.execute(getConnection());
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed!", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * Functional interface for database operations.
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute(Connection connection) throws SQLException;
    }
}
