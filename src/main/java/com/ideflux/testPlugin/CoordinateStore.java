package com.ideflux.testPlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages the storage and retrieval of saved location points with associated metadata.
 * This class facilitates saving, loading, and accessing location points,
 * and integrates with a plugin's configuration file for persistent storage.
 */
public class CoordinateStore {

    /**
     * Immutable value object holding a saved location (world + coordinates).
     */
    public record SavedLocation(String worldName, double x, double y, double z) {}

    // The Key is the name (String), the Value is the SavedLocation
    private final Map<String, SavedLocation> points = new HashMap<>();
    private final JavaPlugin plugin;

    public CoordinateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    // Overwrites the existing point if the name already exists, or creates a new one
    public void storePoint(String name, String worldName, double x, double y, double z) {
        points.put(name.toLowerCase(), new SavedLocation(worldName, x, y, z));
    }

    // Returns null if the name does not exist
    public SavedLocation getPoint(String name) {
        return points.get(name.toLowerCase());
    }

    // Provides access to all saved names for tab completion and listing
    public Set<String> getSavedNames() {
        return Set.copyOf(points.keySet()); // ✅ Safe immutable copy
    }

    public void loadData() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("storage.points");

        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String worldName = section.getString(key + ".world", "world");
            double x = section.getDouble(key + ".x");
            double y = section.getDouble(key + ".y");
            double z = section.getDouble(key + ".z");
            points.put(key, new SavedLocation(worldName, x, y, z));
        }
    }

    public void saveData() {
        FileConfiguration config = plugin.getConfig();

        // Clear old data to prevent deleted keys from persisting
        config.set("storage.points", null);

        for (Map.Entry<String, SavedLocation> entry : points.entrySet()) {
            String name = entry.getKey();
            SavedLocation loc = entry.getValue();

            config.set("storage.points." + name + ".world", loc.worldName());
            config.set("storage.points." + name + ".x", loc.x());
            config.set("storage.points." + name + ".y", loc.y());
            config.set("storage.points." + name + ".z", loc.z());
        }

        plugin.saveConfig();
    }
}