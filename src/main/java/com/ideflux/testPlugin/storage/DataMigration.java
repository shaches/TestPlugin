package com.ideflux.testPlugin.storage;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.UUID;

/**
 * Handles migration from YAML-based storage to SQLite database.
 * Runs automatically on plugin startup if YAML data exists.
 */
public class DataMigration {
    
    private final JavaPlugin plugin;
    private final CoordinateStore coordinateStore;
    private final LocationRepository repository;
    
    public DataMigration(JavaPlugin plugin, CoordinateStore coordinateStore, LocationRepository repository) {
        this.plugin = plugin;
        this.coordinateStore = coordinateStore;
        this.repository = repository;
    }
    
    /**
     * Checks if migration is needed and performs it if necessary.
     * Migration is needed if:
     * 1. YAML config contains location data
     * 2. Migration hasn't been completed before
     */
    public void migrateIfNeeded() {
        FileConfiguration config = plugin.getConfig();
        
        // Check if YAML data exists
        if (!config.contains("storage.points")) {
            plugin.getLogger().info("No YAML data found - skipping migration.");
            return;
        }
        
        // Check if migration was already completed
        if (config.getBoolean("storage.migrated_to_sqlite", false)) {
            plugin.getLogger().info("Data already migrated to SQLite.");
            return;
        }
        
        plugin.getLogger().info("Starting migration from YAML to SQLite...");
        
        try {
            // Load data from YAML
            Map<UUID, Map<String, SavedLocation>> yamlData = coordinateStore.loadDataFromYAML();
            
            if (yamlData.isEmpty()) {
                plugin.getLogger().info("No location data to migrate.");
                markMigrationComplete();
                return;
            }
            
            plugin.getLogger().info("Found " + yamlData.size() + " players with saved locations.");
            
            // Count total locations
            int totalLocations = yamlData.values().stream()
                    .mapToInt(Map::size)
                    .sum();
            
            plugin.getLogger().info("Migrating " + totalLocations + " locations from YAML to SQLite...");
            
            // Import to database
            repository.batchImportLocations(yamlData).thenRun(() -> {
                plugin.getLogger().info("Successfully migrated " + totalLocations + " locations to SQLite!");

                // Must return to main thread for config operations
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Mark migration as complete
                    markMigrationComplete();

                    // Create backup of old YAML data
                    createYamlBackup();
                });

            }).exceptionally(ex -> {
                plugin.getLogger().severe("Migration failed after processing YAML data: " + ex.getMessage());
                plugin.getLogger().severe("Your YAML data is still intact. Please report this error.");
                ex.printStackTrace();
                return null;
            });
            
        } catch (Exception e) {
            plugin.getLogger().severe("Critical error during migration initialization: " + e.getMessage());
            plugin.getLogger().severe("Migration aborted. Your YAML data is still intact.");
            e.printStackTrace();
        }
    }
    
    /**
     * Marks migration as complete in the config.
     * MUST be called on main thread due to FileConfiguration thread-safety requirements.
     */
    private void markMigrationComplete() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("markMigrationComplete() called from non-main thread!");
        }
        FileConfiguration config = plugin.getConfig();
        config.set("storage.migrated_to_sqlite", true);
        plugin.saveConfig();
        plugin.getLogger().info("Migration marked as complete.");
    }
    
    /**
     * Creates a backup of the YAML data before clearing it.
     * MUST be called on main thread due to FileConfiguration thread-safety requirements.
     */
    private void createYamlBackup() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("createYamlBackup() called from non-main thread!");
        }
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            File backupFile = new File(plugin.getDataFolder(), "config.yml.backup");

            if (configFile.exists()) {
                java.nio.file.Files.copy(
                    configFile.toPath(),
                    backupFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                );
                plugin.getLogger().info("Created backup at: config.yml.backup");

                // Clear the YAML data from active config
                FileConfiguration config = plugin.getConfig();
                config.set("storage.points", null);
                plugin.saveConfig();
                plugin.getLogger().info("Cleared YAML location data (backup preserved).");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create YAML backup: " + e.getMessage());
            plugin.getLogger().warning("Migration was successful, but backup creation failed. Original config.yml is still intact.");
        }
    }
}
