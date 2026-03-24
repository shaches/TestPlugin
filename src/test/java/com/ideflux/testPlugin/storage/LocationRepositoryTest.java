package com.ideflux.testPlugin.storage;

import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for LocationRepository using in-memory SQLite database
 * to verify asynchronous CRUD operations and correct UPSERT conflict resolution.
 */
class LocationRepositoryTest {

    @TempDir
    Path tempDir;

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    private DatabaseManager databaseManager;
    private LocationRepository repository;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() throws ExecutionException, InterruptedException {
        mocks = MockitoAnnotations.openMocks(this);

        // Mock plugin data folder
        File dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);

        // Initialize database
        databaseManager = new DatabaseManager(mockPlugin);
        databaseManager.initializeAsync().get();

        // Initialize repository
        repository = new LocationRepository(databaseManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (databaseManager != null) {
            databaseManager.close();
        }
        mocks.close();
    }

    @Test
    void testSaveLocation_InsertsNewLocation() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        String name = "home";
        String worldName = "world";
        double x = 100.5, y = 64.0, z = 200.5;

        // When
        repository.saveLocation(ownerId, name, worldName, x, y, z).get();

        // Then
        SavedLocation loc = repository.getLocation(ownerId, name).get();
        assertNotNull(loc);
        assertEquals(worldName, loc.worldName());
        assertEquals(x, loc.x(), 0.001);
        assertEquals(y, loc.y(), 0.001);
        assertEquals(z, loc.z(), 0.001);
    }

    @Test
    void testSaveLocation_UpdatesExistingLocation() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        String name = "spawn";
        
        // Insert initial location
        repository.saveLocation(ownerId, name, "world", 0.0, 64.0, 0.0).get();

        // When - update with new coordinates
        repository.saveLocation(ownerId, name, "world_nether", 100.0, 32.0, 200.0).get();

        // Then - should be updated, not duplicated
        SavedLocation loc = repository.getLocation(ownerId, name).get();
        assertNotNull(loc);
        assertEquals("world_nether", loc.worldName());
        assertEquals(100.0, loc.x(), 0.001);
        assertEquals(32.0, loc.y(), 0.001);
        assertEquals(200.0, loc.z(), 0.001);
    }

    @Test
    void testSaveLocation_CaseInsensitive() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        
        // Save with uppercase
        repository.saveLocation(ownerId, "HOME", "world", 0.0, 64.0, 0.0).get();

        // When - save with lowercase (should update, not create duplicate)
        repository.saveLocation(ownerId, "home", "world", 100.0, 64.0, 100.0).get();

        // Then
        SavedLocation loc = repository.getLocation(ownerId, "home").get();
        assertEquals(100.0, loc.x(), 0.001);

        // Verify only one location exists
        Map<String, SavedLocation> allLocs = repository.getAllLocations(ownerId).get();
        assertEquals(1, allLocs.size());
    }

    @Test
    void testSaveLocation_NullParameters_ThrowsException() {
        // When & Then
        assertThrows(ExecutionException.class, () -> 
            repository.saveLocation(null, "home", "world", 0, 0, 0).get()
        );
        
        UUID uuid = UUID.randomUUID();
        assertThrows(ExecutionException.class, () -> 
            repository.saveLocation(uuid, null, "world", 0, 0, 0).get()
        );
        
        assertThrows(ExecutionException.class, () -> 
            repository.saveLocation(uuid, "home", null, 0, 0, 0).get()
        );
    }

    @Test
    void testGetLocation_ReturnsNullForNonexistent() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        SavedLocation loc = repository.getLocation(ownerId, "nonexistent").get();

        // Then
        assertNull(loc);
    }

    @Test
    void testGetAllLocations_ReturnsAllPlayerLocations() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        repository.saveLocation(ownerId, "home", "world", 0, 64, 0).get();
        repository.saveLocation(ownerId, "base", "world", 100, 64, 100).get();
        repository.saveLocation(ownerId, "farm", "world", 200, 64, 200).get();

        // When
        Map<String, SavedLocation> locations = repository.getAllLocations(ownerId).get();

        // Then
        assertEquals(3, locations.size());
        assertTrue(locations.containsKey("home"));
        assertTrue(locations.containsKey("base"));
        assertTrue(locations.containsKey("farm"));
    }

    @Test
    void testGetAllLocations_ReturnsEmptyForNoLocations() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        Map<String, SavedLocation> locations = repository.getAllLocations(ownerId).get();

        // Then
        assertTrue(locations.isEmpty());
    }

    @Test
    void testGetAllLocations_NullParameter_ThrowsException() {
        // When & Then
        assertThrows(ExecutionException.class, () -> 
            repository.getAllLocations(null).get()
        );
    }

    @Test
    void testDeleteLocation_RemovesLocation() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        repository.saveLocation(ownerId, "temp", "world", 0, 64, 0).get();

        // When
        Boolean deleted = repository.deleteLocation(ownerId, "temp").get();

        // Then
        assertTrue(deleted);
        assertNull(repository.getLocation(ownerId, "temp").get());
    }

    @Test
    void testDeleteLocation_ReturnsFalseForNonexistent() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();

        // When
        Boolean deleted = repository.deleteLocation(ownerId, "nonexistent").get();

        // Then
        assertFalse(deleted);
    }

    @Test
    void testDeleteLocation_NullParameters_ThrowsException() {
        // When & Then
        assertThrows(ExecutionException.class, () -> 
            repository.deleteLocation(null, "home").get()
        );
        
        UUID uuid = UUID.randomUUID();
        assertThrows(ExecutionException.class, () -> 
            repository.deleteLocation(uuid, null).get()
        );
    }

    @Test
    void testGetAllOwners_ReturnsAllPlayersWithLocations() throws ExecutionException, InterruptedException {
        // Given
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();
        
        repository.saveLocation(owner1, "home", "world", 0, 64, 0).get();
        repository.saveLocation(owner2, "base", "world", 100, 64, 100).get();
        repository.saveLocation(owner3, "farm", "world", 200, 64, 200).get();

        // When
        Set<UUID> owners = repository.getAllOwners().get();

        // Then
        assertEquals(3, owners.size());
        assertTrue(owners.contains(owner1));
        assertTrue(owners.contains(owner2));
        assertTrue(owners.contains(owner3));
    }

    @Test
    void testGetAllOwners_ReturnsEmptySetWhenNoLocations() throws ExecutionException, InterruptedException {
        // When
        Set<UUID> owners = repository.getAllOwners().get();

        // Then
        assertTrue(owners.isEmpty());
    }

    @Test
    void testUpdateUsernameCache_InsertsNewEntry() throws ExecutionException, InterruptedException {
        // Given
        String username = "TestPlayer";
        UUID uuid = UUID.randomUUID();

        // When
        repository.updateUsernameCache(username, uuid).get();

        // Then
        UUID retrieved = repository.getUuidFromCache(username).get();
        assertEquals(uuid, retrieved);
    }

    @Test
    void testUpdateUsernameCache_UpdatesExistingEntry() throws ExecutionException, InterruptedException {
        // Given
        String username = "TestPlayer";
        UUID oldUuid = UUID.randomUUID();
        UUID newUuid = UUID.randomUUID();
        
        repository.updateUsernameCache(username, oldUuid).get();

        // When - update with new UUID
        repository.updateUsernameCache(username, newUuid).get();

        // Then
        UUID retrieved = repository.getUuidFromCache(username).get();
        assertEquals(newUuid, retrieved);
    }

    @Test
    void testUpdateUsernameCache_CaseInsensitive() throws ExecutionException, InterruptedException {
        // Given
        UUID uuid = UUID.randomUUID();
        repository.updateUsernameCache("TestPlayer", uuid).get();

        // When - query with different case
        UUID retrieved = repository.getUuidFromCache("testplayer").get();

        // Then
        assertEquals(uuid, retrieved);
    }

    @Test
    void testUpdateUsernameCache_NullParameters_ThrowsException() {
        // When & Then
        UUID uuid = UUID.randomUUID();
        assertThrows(ExecutionException.class, () -> 
            repository.updateUsernameCache(null, uuid).get()
        );
        
        assertThrows(ExecutionException.class, () -> 
            repository.updateUsernameCache("player", null).get()
        );
    }

    @Test
    void testGetUuidFromCache_ReturnsNullForNonexistent() throws ExecutionException, InterruptedException {
        // When
        UUID retrieved = repository.getUuidFromCache("NonexistentPlayer").get();

        // Then
        assertNull(retrieved);
    }

    @Test
    void testBatchImportLocations_ImportsAllData() throws ExecutionException, InterruptedException {
        // Given
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        
        Map<String, SavedLocation> owner1Locs = new HashMap<>();
        owner1Locs.put("home", new SavedLocation("world", 0, 64, 0));
        owner1Locs.put("base", new SavedLocation("world", 100, 64, 100));
        
        Map<String, SavedLocation> owner2Locs = new HashMap<>();
        owner2Locs.put("spawn", new SavedLocation("world", 50, 64, 50));
        
        Map<UUID, Map<String, SavedLocation>> data = new HashMap<>();
        data.put(owner1, owner1Locs);
        data.put(owner2, owner2Locs);

        // When
        repository.batchImportLocations(data).get();

        // Then
        Map<String, SavedLocation> owner1Retrieved = repository.getAllLocations(owner1).get();
        assertEquals(2, owner1Retrieved.size());
        assertTrue(owner1Retrieved.containsKey("home"));
        assertTrue(owner1Retrieved.containsKey("base"));

        Map<String, SavedLocation> owner2Retrieved = repository.getAllLocations(owner2).get();
        assertEquals(1, owner2Retrieved.size());
        assertTrue(owner2Retrieved.containsKey("spawn"));
    }

    @Test
    void testBatchImportLocations_HandlesEmptyData() throws ExecutionException, InterruptedException {
        // Given
        Map<UUID, Map<String, SavedLocation>> emptyData = new HashMap<>();

        // When & Then - should complete without error
        CompletableFuture<Void> future = repository.batchImportLocations(emptyData);
        assertDoesNotThrow(() -> future.get());
    }

    @Test
    void testBatchImportLocations_ReplacesExistingData() throws ExecutionException, InterruptedException {
        // Given
        UUID owner = UUID.randomUUID();
        repository.saveLocation(owner, "old", "world", 0, 64, 0).get();
        
        Map<String, SavedLocation> newLocs = new HashMap<>();
        newLocs.put("old", new SavedLocation("world", 999, 64, 999));
        
        Map<UUID, Map<String, SavedLocation>> data = new HashMap<>();
        data.put(owner, newLocs);

        // When
        repository.batchImportLocations(data).get();

        // Then
        SavedLocation loc = repository.getLocation(owner, "old").get();
        assertEquals(999.0, loc.x(), 0.001);
    }

    @Test
    void testGetLocationNames_ReturnsAllNames() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        repository.saveLocation(ownerId, "home", "world", 0, 64, 0).get();
        repository.saveLocation(ownerId, "base", "world", 100, 64, 100).get();
        repository.saveLocation(ownerId, "farm", "world", 200, 64, 200).get();

        // When
        Set<String> names = repository.getLocationNames(ownerId).get();

        // Then
        assertEquals(3, names.size());
        assertTrue(names.contains("home"));
        assertTrue(names.contains("base"));
        assertTrue(names.contains("farm"));
    }

    @Test
    void testConcurrentOperations_HandleMultipleSimultaneousWrites() throws ExecutionException, InterruptedException {
        // Given
        UUID ownerId = UUID.randomUUID();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When - perform multiple concurrent writes
        for (int i = 0; i < 10; i++) {
            String name = "loc" + i;
            futures.add(repository.saveLocation(ownerId, name, "world", i, 64, i));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();

        // Then
        Map<String, SavedLocation> locations = repository.getAllLocations(ownerId).get();
        assertEquals(10, locations.size());
    }
}
