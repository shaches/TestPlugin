package com.ideflux.testPlugin;

import com.ideflux.testPlugin.storage.DatabaseManager;
import com.ideflux.testPlugin.storage.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe manager for storage and retrieval of saved location points with per-player ownership.
 * Each player has their own isolated map of named locations with full CRUD access.
 * Other players have read-only access to view and use (but not modify) these locations.
 *
 * Now uses SQLite database for scalable storage instead of YAML.
 * Maintains in-memory cache for fast lookups with database persistence.
 * 
 * Thread Safety:
 * - Uses ConcurrentHashMap for thread-safe in-memory cache
 * - Database operations are async and don't block the main thread
 * - All public methods are thread-safe
 */
public class CoordinateStore {

    /**
     * Immutable value object holding a saved location (world + coordinates).
     */
    public record SavedLocation(String worldName, double x, double y, double z) {}

    // In-memory cache: UUID -> (location name -> SavedLocation)
    // Using ConcurrentHashMap for thread-safe operations
    private final Map<UUID, Map<String, SavedLocation>> locationCache = new ConcurrentHashMap<>();

    // Username-to-UUID cache to prevent blocking Mojang API calls (in-memory only for speed)
    private final Map<String, UUID> usernameCache = new ConcurrentHashMap<>();

    private final JavaPlugin plugin;
    private final DatabaseManager dbManager;
    private final LocationRepository repository;

    public CoordinateStore(JavaPlugin plugin, DatabaseManager dbManager) {
        this.plugin = plugin;
        this.dbManager = dbManager;
        this.repository = new LocationRepository(dbManager);
        loadDataFromDatabase();
    }

    /**
     * Stores a point for the specified owner. Only the owner can create or modify their locations.
     * Thread-safe: Updates both cache and database asynchronously.
     */
    public void storePoint(UUID ownerId, String name, String worldName, double x, double y, double z) {
        SavedLocation location = new SavedLocation(worldName, x, y, z);
        
        // Update in-memory cache immediately
        locationCache.computeIfAbsent(ownerId, k -> new ConcurrentHashMap<>())
                .put(name.toLowerCase(), location);
        
        // Persist to database asynchronously
        repository.saveLocation(ownerId, name, worldName, x, y, z)
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Failed to save location to database: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Retrieves a point owned by the specified player. Returns null if not found.
     * Thread-safe: Reads from in-memory cache for fast access.
     */
    public SavedLocation getPoint(UUID ownerId, String name) {
        Map<String, SavedLocation> ownerMap = locationCache.get(ownerId);
        if (ownerMap == null) return null;
        return ownerMap.get(name.toLowerCase());
    }

    /**
     * Deletes a saved location owned by the specified player.
     * Returns true if the location was deleted, false if it didn't exist.
     * Thread-safe: Updates both cache and database.
     */
    public boolean deletePoint(UUID ownerId, String name) {
        Map<String, SavedLocation> ownerMap = locationCache.get(ownerId);
        if (ownerMap == null) return false;
        
        boolean removed = ownerMap.remove(name.toLowerCase()) != null;
        
        // Clean up empty player maps to prevent bloat
        if (ownerMap.isEmpty()) {
            locationCache.remove(ownerId);
        }
        
        if (removed) {
            // Delete from database asynchronously
            repository.deleteLocation(ownerId, name)
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Failed to delete location from database: " + ex.getMessage());
                        return false;
                    });
        }
        
        return removed;
    }

    /**
     * Returns all location names owned by the specified player.
     * Thread-safe: Reads from in-memory cache.
     */
    public Set<String> getSavedNames(UUID ownerId) {
        Map<String, SavedLocation> ownerMap = locationCache.get(ownerId);
        if (ownerMap == null) return Collections.emptySet();
        return Set.copyOf(ownerMap.keySet());
    }

    /**
     * Returns all player UUIDs that have saved locations.
     * Thread-safe: Reads from in-memory cache.
     */
    public Set<UUID> getAllOwners() {
        return Set.copyOf(locationCache.keySet());
    }

    /**
     * Resolves a username to UUID using cache-first lookup to prevent blocking API calls.
     * Returns null if the player is not online and not in cache.
     * Thread-safe: ConcurrentHashMap handles concurrent access.
     */
    public UUID resolvePlayerUUID(String username) {
        // Check online players first (always current)
        Player onlinePlayer = Bukkit.getPlayerExact(username);
        if (onlinePlayer != null) {
            UUID uuid = onlinePlayer.getUniqueId();
            usernameCache.put(username.toLowerCase(), uuid);
            return uuid;
        }

        // Check cache
        return usernameCache.get(username.toLowerCase());
    }

    /**
     * Updates the username cache. Should be called on player join.
     * Thread-safe: ConcurrentHashMap handles concurrent access.
     * Also persists to database for long-term caching.
     */
    public void updateUsernameCache(String username, UUID uuid) {
        usernameCache.put(username.toLowerCase(), uuid);
        
        // Persist to database asynchronously
        repository.updateUsernameCache(username, uuid)
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Failed to update username cache in database: " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Loads all data from database into memory cache.
     * Called during plugin initialization.
     */
    private void loadDataFromDatabase() {
        plugin.getLogger().info("Loading location data from database...");
        
        repository.getAllOwners().thenAccept(owners -> {
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            
            for (UUID ownerId : owners) {
                CompletableFuture<Void> future = repository.getAllLocations(ownerId)
                        .thenAccept(locations -> {
                            if (!locations.isEmpty()) {
                                locationCache.put(ownerId, new ConcurrentHashMap<>(locations));
                            }
                            
                            // Build username cache
                            org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerId);
                            String playerName = offlinePlayer.getName();
                            if (playerName != null) {
                                usernameCache.put(playerName.toLowerCase(), ownerId);
                            }
                        });
                futures.add(future);
            }
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> plugin.getLogger().info("Loaded " + locationCache.size() + " players with saved locations."))
                    .exceptionally(ex -> {
                        plugin.getLogger().severe("Error loading data from database: " + ex.getMessage());
                        return null;
                    });
        });
    }
    
    /**
     * Legacy method for loading data from YAML config.
     * Used for migration purposes only.
     */
    public Map<UUID, Map<String, SavedLocation>> loadDataFromYAML() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection storageSection = config.getConfigurationSection("storage.points");

        Map<UUID, Map<String, SavedLocation>> data = new HashMap<>();
        
        if (storageSection == null) return data;

        for (String uuidString : storageSection.getKeys(false)) {
            try {
                UUID ownerId = UUID.fromString(uuidString);
                ConfigurationSection playerSection = storageSection.getConfigurationSection(uuidString);

                if (playerSection == null) continue;

                Map<String, SavedLocation> locations = new HashMap<>();
                for (String locationName : playerSection.getKeys(false)) {
                    String worldName = playerSection.getString(locationName + ".world", "world");
                    double x = playerSection.getDouble(locationName + ".x");
                    double y = playerSection.getDouble(locationName + ".y");
                    double z = playerSection.getDouble(locationName + ".z");
                    locations.put(locationName, new SavedLocation(worldName, x, y, z));
                }

                data.put(ownerId, locations);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in config: " + uuidString);
            }
        }
        
        return data;
    }

    /**
     * No longer needed - data is automatically persisted to database on each operation.
     * Kept for backward compatibility during migration.
     * @deprecated Use database persistence instead
     */
    @Deprecated
    public void saveData() {
        plugin.getLogger().info("Legacy saveData() called - data is now automatically persisted to database");
    }
}