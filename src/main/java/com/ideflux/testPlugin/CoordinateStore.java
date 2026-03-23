package com.ideflux.testPlugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Manages the storage and retrieval of saved location points with per-player ownership.
 * Each player has their own isolated map of named locations with full CRUD access.
 * Other players have read-only access to view and use (but not modify) these locations.
 *
 * Includes a username-to-UUID cache to prevent blocking API calls on the main thread.
 */
public class CoordinateStore {

    /**
     * Immutable value object holding a saved location (world + coordinates).
     */
    public record SavedLocation(String worldName, double x, double y, double z) {}

    // Nested map: UUID -> (location name -> SavedLocation)
    private final Map<UUID, Map<String, SavedLocation>> playerPoints = new HashMap<>();

    // Username-to-UUID cache to prevent blocking Mojang API calls
    private final Map<String, UUID> usernameCache = new HashMap<>();

    private final JavaPlugin plugin;

    public CoordinateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    /**
     * Stores a point for the specified owner. Only the owner can create or modify their locations.
     */
    public void storePoint(UUID ownerId, String name, String worldName, double x, double y, double z) {
        playerPoints.computeIfAbsent(ownerId, k -> new HashMap<>())
                .put(name.toLowerCase(), new SavedLocation(worldName, x, y, z));

        // Add immediate disk persistence to prevent data loss on crash
        saveData();
    }

    /**
     * Retrieves a point owned by the specified player. Returns null if not found.
     */
    public SavedLocation getPoint(UUID ownerId, String name) {
        Map<String, SavedLocation> ownerMap = playerPoints.get(ownerId);
        if (ownerMap == null) return null;
        return ownerMap.get(name.toLowerCase());
    }

    /**
     * Returns all location names owned by the specified player.
     */
    public Set<String> getSavedNames(UUID ownerId) {
        Map<String, SavedLocation> ownerMap = playerPoints.get(ownerId);
        if (ownerMap == null) return Collections.emptySet();
        return Set.copyOf(ownerMap.keySet());
    }

    /**
     * Returns all player UUIDs that have saved locations.
     */
    public Set<UUID> getAllOwners() {
        return Set.copyOf(playerPoints.keySet());
    }

    /**
     * Resolves a username to UUID using cache-first lookup to prevent blocking API calls.
     * Returns null if the player is not online and not in cache.
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
     */
    public void updateUsernameCache(String username, UUID uuid) {
        usernameCache.put(username.toLowerCase(), uuid);
    }

    public void loadData() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection storageSection = config.getConfigurationSection("storage.points");

        if (storageSection == null) return;

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

                playerPoints.put(ownerId, locations);

                // Build username cache from loaded data (UUID lookup is safe and non-blocking)
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(ownerId);
                String playerName = offlinePlayer.getName();
                if (playerName != null) {
                    usernameCache.put(playerName.toLowerCase(), ownerId);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID in config: " + uuidString);
            }
        }
    }

    public void saveData() {
        FileConfiguration config = plugin.getConfig();

        // Clear old data to prevent deleted keys from persisting
        config.set("storage.points", null);

        for (Map.Entry<UUID, Map<String, SavedLocation>> playerEntry : playerPoints.entrySet()) {
            String uuidString = playerEntry.getKey().toString();
            Map<String, SavedLocation> locations = playerEntry.getValue();

            for (Map.Entry<String, SavedLocation> locEntry : locations.entrySet()) {
                String name = locEntry.getKey();
                SavedLocation loc = locEntry.getValue();

                String path = "storage.points." + uuidString + "." + name;
                config.set(path + ".world", loc.worldName());
                config.set(path + ".x", loc.x());
                config.set(path + ".y", loc.y());
                config.set(path + ".z", loc.z());
            }
        }

        plugin.saveConfig();
    }
}