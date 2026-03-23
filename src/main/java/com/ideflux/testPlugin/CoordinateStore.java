package com.ideflux.testPlugin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CoordinateStore {

    // The Key is the name (String), the Value is the coordinate array (double[])
    private final Map<String, double[]> points = new HashMap<>();
    private final JavaPlugin plugin;

    public CoordinateStore(JavaPlugin plugin) {
        this.plugin = plugin;
        loadData();
    }

    // Overwrites the existing point if the name already exists, or creates a new one
    public void storePoint(String name, double[] thePoint) {
        points.put(name.toLowerCase(), thePoint);
    }

    // Returns null if the name does not exist
    public double[] getPoint(String name) {
        return points.get(name.toLowerCase());
    }

    // Provides access to all saved names for tab completion and listing
    public Set<String> getSavedNames() {
        return points.keySet();
    }

    public void loadData() {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("storage.points");

        if (section == null) return;

        // Iterate through all string keys in the config
        for (String key : section.getKeys(false)) {
            double x = section.getDouble(key + ".x");
            double y = section.getDouble(key + ".y");
            double z = section.getDouble(key + ".z");
            points.put(key, new double[]{x, y, z});
        }
    }

    public void saveData() {
        FileConfiguration config = plugin.getConfig();

        // Clear old data to prevent deleted keys from persisting
        config.set("storage.points", null);

        for (Map.Entry<String, double[]> entry : points.entrySet()) {
            String name = entry.getKey();
            double[] coords = entry.getValue();

            config.set("storage.points." + name + ".x", coords[0]);
            config.set("storage.points." + name + ".y", coords[1]);
            config.set("storage.points." + name + ".z", coords[2]);
        }

        plugin.saveConfig();
    }
}