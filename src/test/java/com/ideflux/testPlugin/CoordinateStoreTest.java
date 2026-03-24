package com.ideflux.testPlugin;

import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import com.ideflux.testPlugin.storage.DatabaseManager;
import com.ideflux.testPlugin.storage.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CoordinateStore to validate thread-safe ConcurrentHashMap caching,
 * player location quota enforcement, and username-to-UUID caching logic.
 */
@Execution(ExecutionMode.SAME_THREAD)
class CoordinateStoreTest {

    @TempDir
    Path tempDir;

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    @Mock
    private FileConfiguration mockConfig;

    @Mock
    private Player mockPlayer;

    @Mock
    private Server mockServer;

    private DatabaseManager databaseManager;
    private CoordinateStore coordinateStore;
    private MockedStatic<Bukkit> bukkit;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock plugin
        File dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);
        when(mockPlugin.getConfig()).thenReturn(mockConfig);
        when(mockPlugin.getServer()).thenReturn(mockServer);

        // Mock config defaults
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(50);

        // Initialize database
        databaseManager = new DatabaseManager(mockPlugin);
        databaseManager.initializeAsync().get();

        // Initialize coordinate store
        coordinateStore = new CoordinateStore(mockPlugin, databaseManager);

        // Mock Bukkit static methods
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        coordinateStore = null;

        // Wait for all async database operations to finish before closing the database
        java.util.concurrent.ForkJoinPool.commonPool().awaitQuiescence(5, java.util.concurrent.TimeUnit.SECONDS);

        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
        if (bukkit != null) {
            bukkit.close();
        }
        mocks.close();

        // Brief pause to allow the OS to complete file handle release
        Thread.sleep(250);
    }

    @Test
    void testStorePoint_SavesLocationInCache() {
        // Given
        UUID ownerId = UUID.randomUUID();
        String name = "home";

        // When
        coordinateStore.storePoint(ownerId, name, "world", 100.0, 64.0, 200.0);

        // Then
        SavedLocation loc = coordinateStore.getPoint(ownerId, name);
        assertNotNull(loc);
        assertEquals("world", loc.worldName());
        assertEquals(100.0, loc.x(), 0.001);
        assertEquals(64.0, loc.y(), 0.001);
        assertEquals(200.0, loc.z(), 0.001);
    }

    @Test
    void testStorePoint_UpdatesExistingLocation() {
        // Given
        UUID ownerId = UUID.randomUUID();
        String name = "spawn";
        coordinateStore.storePoint(ownerId, name, "world", 0.0, 64.0, 0.0);

        // When - update with new coordinates
        coordinateStore.storePoint(ownerId, name, "world_nether", 100.0, 32.0, 200.0);

        // Then
        SavedLocation loc = coordinateStore.getPoint(ownerId, name);
        assertEquals("world_nether", loc.worldName());
        assertEquals(100.0, loc.x(), 0.001);
    }

    @Test
    void testStorePoint_CaseInsensitive() {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        coordinateStore.storePoint(ownerId, "HOME", "world", 0.0, 64.0, 0.0);
        coordinateStore.storePoint(ownerId, "home", "world", 100.0, 64.0, 100.0);

        // Then - should update, not create duplicate
        assertEquals(1, coordinateStore.getLocationCount(ownerId));
        SavedLocation loc = coordinateStore.getPoint(ownerId, "home");
        assertEquals(100.0, loc.x(), 0.001);
    }

    @Test
    void testStorePoint_HandlesNullParameters() {
        // When & Then - should not throw exception
        assertDoesNotThrow(() -> {
            coordinateStore.storePoint(null, "home", "world", 0, 64, 0);
            coordinateStore.storePoint(UUID.randomUUID(), null, "world", 0, 64, 0);
            coordinateStore.storePoint(UUID.randomUUID(), "home", null, 0, 64, 0);
        });
    }

    @Test
    void testGetPoint_ReturnsNullForNonexistent() {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        SavedLocation loc = coordinateStore.getPoint(ownerId, "nonexistent");

        // Then
        assertNull(loc);
    }

    @Test
    void testGetPoint_HandlesNullParameters() {
        // When & Then
        assertNull(coordinateStore.getPoint(null, "home"));
        assertNull(coordinateStore.getPoint(UUID.randomUUID(), null));
    }

    @Test
    void testDeletePoint_RemovesLocation() {
        // Given
        UUID ownerId = UUID.randomUUID();
        coordinateStore.storePoint(ownerId, "temp", "world", 0, 64, 0);

        // When
        boolean deleted = coordinateStore.deletePoint(ownerId, "temp");

        // Then
        assertTrue(deleted);
        assertNull(coordinateStore.getPoint(ownerId, "temp"));
    }

    @Test
    void testDeletePoint_ReturnsFalseForNonexistent() {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        boolean deleted = coordinateStore.deletePoint(ownerId, "nonexistent");

        // Then
        assertFalse(deleted);
    }

    @Test
    void testDeletePoint_HandlesNullParameters() {
        // When & Then
        assertFalse(coordinateStore.deletePoint(null, "home"));
        assertFalse(coordinateStore.deletePoint(UUID.randomUUID(), null));
    }

    @Test
    void testGetSavedNames_ReturnsAllNames() {
        // Given
        UUID ownerId = UUID.randomUUID();
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);
        coordinateStore.storePoint(ownerId, "base", "world", 100, 64, 100);
        coordinateStore.storePoint(ownerId, "farm", "world", 200, 64, 200);

        // When
        Set<String> names = coordinateStore.getSavedNames(ownerId);

        // Then
        assertEquals(3, names.size());
        assertTrue(names.contains("home"));
        assertTrue(names.contains("base"));
        assertTrue(names.contains("farm"));
    }

    @Test
    void testGetSavedNames_ReturnsEmptySetForNoLocations() {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        Set<String> names = coordinateStore.getSavedNames(ownerId);

        // Then
        assertTrue(names.isEmpty());
    }

    @Test
    void testGetSavedNames_HandlesNullParameter() {
        // When
        Set<String> names = coordinateStore.getSavedNames(null);

        // Then
        assertTrue(names.isEmpty());
    }

    @Test
    void testGetLocationCount_ReturnsCorrectCount() {
        // Given
        UUID ownerId = UUID.randomUUID();
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);
        coordinateStore.storePoint(ownerId, "base", "world", 100, 64, 100);

        // When
        int count = coordinateStore.getLocationCount(ownerId);

        // Then
        assertEquals(2, count);
    }

    @Test
    void testGetLocationCount_ReturnsZeroForNoLocations() {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        int count = coordinateStore.getLocationCount(ownerId);

        // Then
        assertEquals(0, count);
    }

    @Test
    void testGetLocationCount_HandlesNullParameter() {
        // When
        int count = coordinateStore.getLocationCount(null);

        // Then
        assertEquals(0, count);
    }

    @Test
    void testCanSaveLocation_AllowsWithinQuota() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(3);
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);
        coordinateStore.storePoint(ownerId, "base", "world", 100, 64, 100);

        // When - trying to save 3rd location (within limit of 3)
        boolean canSave = coordinateStore.canSaveLocation(ownerId, "farm", false);

        // Then
        assertTrue(canSave);
    }

    @Test
    void testCanSaveLocation_DeniesWhenQuotaExceeded() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(2);
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);
        coordinateStore.storePoint(ownerId, "base", "world", 100, 64, 100);

        // When - trying to save 3rd location (exceeds limit of 2)
        boolean canSave = coordinateStore.canSaveLocation(ownerId, "farm", false);

        // Then
        assertFalse(canSave);
    }

    @Test
    void testCanSaveLocation_AllowsUpdatingExisting() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(1);
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);

        // When - trying to update existing location (should be allowed even if at quota)
        boolean canSave = coordinateStore.canSaveLocation(ownerId, "home", false);

        // Then
        assertTrue(canSave);
    }

    @Test
    void testCanSaveLocation_BypassesQuotaWhenFlagSet() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(1);
        coordinateStore.storePoint(ownerId, "home", "world", 0, 64, 0);

        // When - bypass quota flag is true
        boolean canSave = coordinateStore.canSaveLocation(ownerId, "base", true);

        // Then
        assertTrue(canSave);
    }

    @Test
    void testCanSaveLocation_HandlesUnlimitedQuota() {
        // Given
        UUID ownerId = UUID.randomUUID();
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(-1);
        
        // Add many locations
        for (int i = 0; i < 100; i++) {
            coordinateStore.storePoint(ownerId, "loc" + i, "world", i, 64, i);
        }

        // When - quota is -1 (unlimited)
        boolean canSave = coordinateStore.canSaveLocation(ownerId, "newloc", false);

        // Then
        assertTrue(canSave);
    }

    @Test
    void testGetMaxLocationsPerPlayer_ReturnsConfigValue() {
        // Given
        when(mockConfig.getInt("storage.max-locations-per-player", 50)).thenReturn(25);

        // When
        int max = coordinateStore.getMaxLocationsPerPlayer();

        // Then
        assertEquals(25, max);
    }

    @Test
    void testGetAllOwners_ReturnsAllPlayers() {
        // Given
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();
        
        coordinateStore.storePoint(owner1, "home", "world", 0, 64, 0);
        coordinateStore.storePoint(owner2, "base", "world", 100, 64, 100);
        coordinateStore.storePoint(owner3, "farm", "world", 200, 64, 200);

        // When
        Set<UUID> owners = coordinateStore.getAllOwners();

        // Then
        assertEquals(3, owners.size());
        assertTrue(owners.contains(owner1));
        assertTrue(owners.contains(owner2));
        assertTrue(owners.contains(owner3));
    }

    @Test
    void testResolvePlayerUUID_FindsOnlinePlayer() {
        // Given
        String username = "TestPlayer";
        UUID uuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(uuid);
        bukkit.when(() -> Bukkit.getPlayerExact(username)).thenReturn(mockPlayer);

        // When
        UUID resolved = coordinateStore.resolvePlayerUUID(username);

        // Then
        assertEquals(uuid, resolved);
    }

    @Test
    void testResolvePlayerUUID_UsesCache() {
        // Given
        String username = "TestPlayer";
        UUID uuid = UUID.randomUUID();
        coordinateStore.updateUsernameCache(username, uuid);
        bukkit.when(() -> Bukkit.getPlayerExact(username)).thenReturn(null);

        // When
        UUID resolved = coordinateStore.resolvePlayerUUID(username);

        // Then
        assertEquals(uuid, resolved);
    }

    @Test
    void testResolvePlayerUUID_ReturnsNullWhenNotFound() {
        // Given
        bukkit.when(() -> Bukkit.getPlayerExact("NonexistentPlayer")).thenReturn(null);

        // When
        UUID resolved = coordinateStore.resolvePlayerUUID("NonexistentPlayer");

        // Then
        assertNull(resolved);
    }

    @Test
    void testUpdateUsernameCache_StoresMapping() {
        // Given
        String username = "TestPlayer";
        UUID uuid = UUID.randomUUID();
        bukkit.when(() -> Bukkit.getPlayerExact(username)).thenReturn(null);

        // When
        coordinateStore.updateUsernameCache(username, uuid);

        // Then
        assertEquals(uuid, coordinateStore.resolvePlayerUUID(username));
    }

    @Test
    void testUpdateUsernameCache_CaseInsensitive() {
        // Given
        UUID uuid = UUID.randomUUID();
        coordinateStore.updateUsernameCache("TestPlayer", uuid);
        bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenReturn(null);

        // When
        UUID resolved = coordinateStore.resolvePlayerUUID("testplayer");

        // Then
        assertEquals(uuid, resolved);
    }

    @Test
    void testThreadSafety_ConcurrentOperations() throws InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        int threadCount = 10;
        int operationsPerThread = 100;

        // When - multiple threads storing locations concurrently
        Thread[] threads = new Thread[threadCount];
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            threads[t] = new Thread(() -> {
                for (int i = 0; i < operationsPerThread; i++) {
                    String name = "loc_" + threadId + "_" + i;
                    coordinateStore.storePoint(ownerId, name, "world", i, 64, i);
                }
            });
            threads[t].start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - all locations should be stored correctly
        int expectedCount = threadCount * operationsPerThread;
        assertEquals(expectedCount, coordinateStore.getLocationCount(ownerId));
    }

    @Test
    void testThreadSafety_ConcurrentReadWrite() throws InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        coordinateStore.storePoint(ownerId, "shared", "world", 0, 64, 0);

        // When - multiple threads reading and writing concurrently
        Thread writer = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                coordinateStore.storePoint(ownerId, "shared", "world", i, 64, i);
            }
        });

        Thread reader = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                SavedLocation loc = coordinateStore.getPoint(ownerId, "shared");
                assertNotNull(loc);
            }
        });

        writer.start();
        reader.start();
        writer.join();
        reader.join();

        // Then - should complete without ConcurrentModificationException
        assertNotNull(coordinateStore.getPoint(ownerId, "shared"));
    }
}
