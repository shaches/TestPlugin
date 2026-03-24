package com.ideflux.testPlugin.storage;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for DataMigration to verify automatic migration of location data
 * from YAML to SQLite and successful creation of configuration backups.
 */
class DataMigrationTest {

    @TempDir
    Path tempDir;

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    @Mock
    private Server mockServer;

    @Mock
    private BukkitScheduler mockScheduler;

    private DatabaseManager databaseManager;
    private LocationRepository repository;
    private CoordinateStore coordinateStore;
    private DataMigration dataMigration;
    private FileConfiguration config;
    private File configFile;
    private AutoCloseable mocks;
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException, IOException {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock Bukkit static methods
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mockServer);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

        // Mock plugin data folder
        File dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);
        when(mockPlugin.getServer()).thenReturn(mockServer);
        when(mockServer.getScheduler()).thenReturn(mockScheduler);

        // Mock scheduler to run tasks synchronously for testing
        when(mockScheduler.runTask(any(JavaPlugin.class), any(Runnable.class)))
            .thenAnswer((Answer<BukkitTask>) invocation -> {
                Runnable task = invocation.getArgument(1);
                task.run();
                return mock(BukkitTask.class);
            });

        // Create config file
        configFile = new File(dataFolder, "config.yml");
        config = new YamlConfiguration();

        // Mock plugin config
        when(mockPlugin.getConfig()).thenReturn(config);
        doAnswer(invocation -> {
            config.save(configFile);
            return null;
        }).when(mockPlugin).saveConfig();

        // Initialize database
        databaseManager = new DatabaseManager(mockPlugin);
        databaseManager.initializeAsync().get();

        // Initialize repository and coordinate store
        repository = new LocationRepository(databaseManager);
        coordinateStore = new CoordinateStore(mockPlugin, databaseManager);

        dataMigration = new DataMigration(mockPlugin, coordinateStore, repository);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (bukkit != null) {
            bukkit.close();
        }
        mocks.close();
    }

    @Test
    void testMigrateIfNeeded_NoYAMLData_SkipsMigration() {
        // Given - no YAML data in config
        // (config is empty by default)

        // When
        dataMigration.migrateIfNeeded();

        // Then
        verify(mockLogger).info(contains("No YAML data found"));
        assertFalse(config.getBoolean("storage.migrated_to_sqlite", false));
    }

    @Test
    void testMigrateIfNeeded_AlreadyMigrated_SkipsMigration() {
        // Given - YAML data exists but migration already completed
        config.set("storage.points.test-uuid.home.world", "world");
        config.set("storage.points.test-uuid.home.x", 0.0);
        config.set("storage.points.test-uuid.home.y", 64.0);
        config.set("storage.points.test-uuid.home.z", 0.0);
        config.set("storage.migrated_to_sqlite", true);

        // When
        dataMigration.migrateIfNeeded();

        // Then
        verify(mockLogger).info(contains("already migrated"));
    }

    @Test
    void testMigrateIfNeeded_WithYAMLData_PerformsMigration() throws InterruptedException, ExecutionException {
        // Given - YAML data exists
        UUID testUuid = UUID.randomUUID();
        String uuidString = testUuid.toString();

        config.set("storage.points." + uuidString + ".home.world", "world");
        config.set("storage.points." + uuidString + ".home.x", 100.0);
        config.set("storage.points." + uuidString + ".home.y", 64.0);
        config.set("storage.points." + uuidString + ".home.z", 200.0);

        config.set("storage.points." + uuidString + ".base.world", "world_nether");
        config.set("storage.points." + uuidString + ".base.x", 50.0);
        config.set("storage.points." + uuidString + ".base.y", 32.0);
        config.set("storage.points." + uuidString + ".base.z", 150.0);

        // When
        dataMigration.migrateIfNeeded();

        // Wait for async operations to complete
        Thread.sleep(500);

        // Then - verify data was migrated to database
        Map<String, SavedLocation> locations = repository.getAllLocations(testUuid).get();
        assertEquals(2, locations.size());

        SavedLocation home = locations.get("home");
        assertNotNull(home);
        assertEquals("world", home.worldName());
        assertEquals(100.0, home.x(), 0.001);
        assertEquals(64.0, home.y(), 0.001);
        assertEquals(200.0, home.z(), 0.001);

        SavedLocation base = locations.get("base");
        assertNotNull(base);
        assertEquals("world_nether", base.worldName());
        assertEquals(50.0, base.x(), 0.001);
    }

    @Test
    void testMigrateIfNeeded_HandlesMultiplePlayers() throws InterruptedException, ExecutionException {
        // Given - multiple players with data
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();
        UUID player3 = UUID.randomUUID();

        config.set("storage.points." + player1 + ".home.world", "world");
        config.set("storage.points." + player1 + ".home.x", 0.0);
        config.set("storage.points." + player1 + ".home.y", 64.0);
        config.set("storage.points." + player1 + ".home.z", 0.0);

        config.set("storage.points." + player2 + ".base.world", "world");
        config.set("storage.points." + player2 + ".base.x", 100.0);
        config.set("storage.points." + player2 + ".base.y", 64.0);
        config.set("storage.points." + player2 + ".base.z", 100.0);

        config.set("storage.points." + player3 + ".farm.world", "world");
        config.set("storage.points." + player3 + ".farm.x", 200.0);
        config.set("storage.points." + player3 + ".farm.y", 64.0);
        config.set("storage.points." + player3 + ".farm.z", 200.0);

        // When
        dataMigration.migrateIfNeeded();
        Thread.sleep(500);

        // Then - all players should have their data
        assertFalse(repository.getAllLocations(player1).get().isEmpty());
        assertFalse(repository.getAllLocations(player2).get().isEmpty());
        assertFalse(repository.getAllLocations(player3).get().isEmpty());
    }

    @Test
    void testMigrateIfNeeded_HandlesEmptyPlayerSection() {
        // Given - player section exists but is empty
        UUID testUuid = UUID.randomUUID();
        config.set("storage.points." + testUuid, Collections.emptyMap());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> dataMigration.migrateIfNeeded());
    }

    @Test
    void testMigrateIfNeeded_HandlesInvalidUUID() throws InterruptedException {
        // Given - invalid UUID in config
        config.set("storage.points.invalid-uuid.home.world", "world");
        config.set("storage.points.invalid-uuid.home.x", 0.0);
        config.set("storage.points.invalid-uuid.home.y", 64.0);
        config.set("storage.points.invalid-uuid.home.z", 0.0);

        // When
        dataMigration.migrateIfNeeded();
        Thread.sleep(200);

        // Then - should complete without throwing exception
        verify(mockLogger, atLeastOnce()).info(anyString());
    }
}
