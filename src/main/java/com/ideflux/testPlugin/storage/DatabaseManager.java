package com.ideflux.testPlugin.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages SQLite database connection pool using HikariCP.
 * Provides thread-safe connection pooling and proper resource management.
 * Each async operation gets its own connection from the pool, preventing transaction bleeding.
 */
public class DatabaseManager {

    private final JavaPlugin plugin;
    private final String databasePath;
    private HikariDataSource dataSource;
    
    public DatabaseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databasePath = plugin.getDataFolder().getAbsolutePath() + File.separator + "locations.db";
    }
    
    /**
     * Initializes the database connection pool and creates tables if needed.
     * Runs asynchronously to prevent blocking the main thread during plugin startup.
     *
     * @return CompletableFuture that completes when initialization is done
     */
    public java.util.concurrent.CompletableFuture<Void> initializeAsync() {
        return java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                // Ensure data folder exists
                if (!plugin.getDataFolder().exists()) {
                    if (!plugin.getDataFolder().mkdirs()) {
                        plugin.getLogger().warning("Failed to create plugin data folder");
                    }
                }

                // Configure HikariCP connection pool
                HikariConfig config = new HikariConfig();
                config.setJdbcUrl("jdbc:sqlite:" + databasePath);
                config.setDriverClassName("org.sqlite.JDBC");

                // Connection pool settings
                config.setMaximumPoolSize(10); // Max 10 concurrent connections
                config.setMinimumIdle(2); // Keep at least 2 idle connections
                config.setConnectionTimeout(10000); // 10 second timeout
                config.setIdleTimeout(600000); // 10 minute idle timeout
                config.setMaxLifetime(1800000); // 30 minute max lifetime

                // SQLite-specific optimizations
                config.addDataSourceProperty("cachePrepStmts", "true");
                config.addDataSourceProperty("prepStmtCacheSize", "250");
                config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

                // Initialize connection pool
                dataSource = new HikariDataSource(config);

                // Configure SQLite pragmas using a connection from the pool
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.execute("PRAGMA foreign_keys = ON");
                    stmt.execute("PRAGMA journal_mode = WAL"); // Write-Ahead Logging for better concurrency
                    stmt.execute("PRAGMA synchronous = NORMAL"); // Balance between safety and performance
                }

                // Create tables
                createTables();

                plugin.getLogger().info("SQLite database pool initialized successfully at: " + databasePath);

            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize database pool!", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        });
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

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createLocationsTable);
            stmt.execute(createIndexOwner);
            stmt.execute(createIndexName);
            stmt.execute(createUsernameCacheTable);
        }
    }
    
    /**
     * Gets a connection from the pool.
     * Each caller gets their own connection, ensuring thread safety and transaction isolation.
     * Callers MUST close the connection when done (use try-with-resources).
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        return dataSource.getConnection();
    }

    /**
     * Closes the database connection pool.
     * Should be called during plugin disable.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection pool closed successfully.");
        }
    }
    
    /**
     * Executes a database operation asynchronously and returns a CompletableFuture.
     * Ensures database operations don't block the main server thread.
     * Each operation gets its own connection from the pool, ensuring thread safety.
     *
     * @param operation The database operation to execute
     * @param <T> The return type of the operation
     * @return CompletableFuture containing the result of the operation
     */
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            // Each async operation gets its own connection from the pool
            try (Connection conn = getConnection()) {
                return operation.execute(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed: " + e.getMessage(), e);
                throw new RuntimeException("Database operation failed", e);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error during database operation: " + e.getMessage(), e);
                throw new RuntimeException("Unexpected database error", e);
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
