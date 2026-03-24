package com.ideflux.testPlugin.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DatabaseManager verifying HikariCP connection pool initialization,
 * property configuration, and connection retrieval.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    private DatabaseManager databaseManager;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        
        // Mock plugin data folder
        File dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);

        databaseManager = new DatabaseManager(mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (databaseManager != null) {
            databaseManager.close();
        }
        mocks.close();
    }

    @Test
    void testInitializeAsync_CreatesDataFolder() throws ExecutionException, InterruptedException {
        // When
        CompletableFuture<Void> future = databaseManager.initializeAsync();
        future.get(); // Wait for completion

        // Then
        assertTrue(mockPlugin.getDataFolder().exists());
        verify(mockLogger, atLeastOnce()).info(contains("database pool initialized"));
    }

    @Test
    void testInitializeAsync_CreatesDatabase() throws ExecutionException, InterruptedException, SQLException {
        // When
        CompletableFuture<Void> future = databaseManager.initializeAsync();
        future.get();

        // Then - verify database file exists
        File dbFile = new File(mockPlugin.getDataFolder(), "locations.db");
        assertTrue(dbFile.exists());
    }

    @Test
    void testInitializeAsync_CreatesTablesWithCorrectSchema() throws ExecutionException, InterruptedException, SQLException {
        // When
        CompletableFuture<Void> future = databaseManager.initializeAsync();
        future.get();

        // Then - verify tables exist
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Check player_locations table
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='player_locations'"
            );
            assertTrue(rs.next(), "player_locations table should exist");

            // Check username_cache table
            rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='username_cache'"
            );
            assertTrue(rs.next(), "username_cache table should exist");
        }
    }

    @Test
    void testInitializeAsync_CreatesIndexes() throws ExecutionException, InterruptedException, SQLException {
        // When
        CompletableFuture<Void> future = databaseManager.initializeAsync();
        future.get();

        // Then - verify indexes exist
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_owner_uuid'"
            );
            assertTrue(rs.next(), "idx_owner_uuid index should exist");

            rs = stmt.executeQuery(
                "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_location_name'"
            );
            assertTrue(rs.next(), "idx_location_name index should exist");
        }
    }

    @Test
    void testGetConnection_ReturnsValidConnection() throws ExecutionException, InterruptedException, SQLException {
        // Given
        databaseManager.initializeAsync().get();

        // When
        try (Connection conn = databaseManager.getConnection()) {
            // Then
            assertNotNull(conn);
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void testGetConnection_ThrowsExceptionWhenNotInitialized() {
        // When & Then
        assertThrows(SQLException.class, () -> databaseManager.getConnection());
    }

    @Test
    void testGetConnection_ReturnsMultipleConnections() throws ExecutionException, InterruptedException, SQLException {
        // Given
        databaseManager.initializeAsync().get();

        // When - get multiple connections simultaneously
        Connection conn1 = databaseManager.getConnection();
        Connection conn2 = databaseManager.getConnection();

        // Then - both should be valid and different
        assertNotNull(conn1);
        assertNotNull(conn2);
        assertNotSame(conn1, conn2);

        conn1.close();
        conn2.close();
    }

    @Test
    void testExecuteAsync_ExecutesOperationSuccessfully() throws ExecutionException, InterruptedException {
        // Given
        databaseManager.initializeAsync().get();

        // When
        CompletableFuture<Integer> future = databaseManager.executeAsync(conn -> {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT INTO player_locations (owner_uuid, location_name, world_name, x, y, z) " +
                           "VALUES ('test-uuid', 'test-location', 'world', 0.0, 0.0, 0.0)");
                return 42; // Return value for testing
            }
        });

        Integer result = future.get();

        // Then
        assertEquals(42, result);
    }

    @Test
    void testExecuteAsync_HandlesExceptionProperly() throws ExecutionException, InterruptedException {
        // Given
        databaseManager.initializeAsync().get();

        // When
        CompletableFuture<Void> future = databaseManager.executeAsync(conn -> {
            throw new SQLException("Test exception");
        });

        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        assertTrue(exception.getCause() instanceof RuntimeException);
        assertTrue(exception.getCause().getMessage().contains("Database operation failed"));
    }

    @Test
    void testClose_ClosesDataSource() throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        // Given
        databaseManager.initializeAsync().get();

        // Use reflection to access private dataSource field
        Field dataSourceField = DatabaseManager.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        HikariDataSource dataSource = (HikariDataSource) dataSourceField.get(databaseManager);

        // When
        databaseManager.close();

        // Then
        assertTrue(dataSource.isClosed());
        verify(mockLogger).info(contains("Database connection pool closed"));
    }

    @Test
    void testClose_HandlesAlreadyClosed() throws ExecutionException, InterruptedException {
        // Given
        databaseManager.initializeAsync().get();

        // When
        databaseManager.close();
        databaseManager.close(); // Close again

        // Then - should not throw exception
        verify(mockLogger, times(1)).info(contains("Database connection pool closed"));
    }

    @Test
    void testHikariCPConfiguration_CorrectPoolSize() throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        // Given
        databaseManager.initializeAsync().get();

        // Use reflection to access private dataSource field
        Field dataSourceField = DatabaseManager.class.getDeclaredField("dataSource");
        dataSourceField.setAccessible(true);
        HikariDataSource dataSource = (HikariDataSource) dataSourceField.get(databaseManager);

        // Then
        assertEquals(10, dataSource.getMaximumPoolSize());
        assertEquals(2, dataSource.getMinimumIdle());
    }

    @Test
    void testSQLitePragmas_AreConfigured() throws ExecutionException, InterruptedException, SQLException {
        // Given
        databaseManager.initializeAsync().get();

        // When
        try (Connection conn = databaseManager.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check foreign_keys pragma
            ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Foreign keys should be enabled");

            // Check journal_mode pragma
            rs = stmt.executeQuery("PRAGMA journal_mode");
            assertTrue(rs.next());
            assertEquals("wal", rs.getString(1).toLowerCase(), "Journal mode should be WAL");

            // Check synchronous pragma
            rs = stmt.executeQuery("PRAGMA synchronous");
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "Synchronous should be NORMAL (1)");
        }
    }
}
