package com.ideflux.testPlugin;

import com.ideflux.testPlugin.commands.DeleteLocCommand;
import com.ideflux.testPlugin.commands.GotoCommand;
import com.ideflux.testPlugin.commands.ListLocsCommand;
import com.ideflux.testPlugin.commands.SetLocCommand;
import com.ideflux.testPlugin.commands.StoreLocCommand;
import com.ideflux.testPlugin.listeners.CommandInterceptor;
import com.ideflux.testPlugin.listeners.PlayerJoinListener;
import com.ideflux.testPlugin.listeners.PlayerQuitListener;
import com.ideflux.testPlugin.listeners.TabCompletionInterceptor;
import com.ideflux.testPlugin.storage.DataMigration;
import com.ideflux.testPlugin.storage.DatabaseManager;
import com.ideflux.testPlugin.storage.LocationRepository;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * The TestPlugin class is the main entry point for the plugin.
 * It handles the initialization and shutdown processes, including the registration of commands and events.
 *
 * Functionalities include:
 * - Loading and saving default configuration files.
 * - Initializing SQLite database for scalable location storage.
 * - Loading customizable messages from messages.yml.
 * - Instantiation of the CoordinateStore for managing saved locations.
 * - Automatic migration from YAML to SQLite on first run.
 * - Registering commands such as:
 *   - `/goto`: Teleports players to saved locations.
 *   - `/listlocs`: Lists all saved location names.
 *   - `/setloc`: Updates or creates saved locations.
 *   - `/storeloc`: Adds a new saved location.
 *   - `/deleteloc`: Deletes a saved location.
 * - Setting up event listeners for tab completion and command interception.
 *
 * This plugin integrates with the Spigot API and uses SQLite for persistent storage.
 */
public class TestPlugin extends JavaPlugin {

    private DatabaseManager dbManager;
    private CoordinateStore crdStore;
    private MessageManager messages;
    private VisibilityCache visibilityCache;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        // Initialize message manager (synchronous - lightweight)
        this.messages = new MessageManager(this);
        
        // Initialize database manager
        this.dbManager = new DatabaseManager(this);
        
        // Initialize coordinate store (no data loaded yet)
        this.crdStore = new CoordinateStore(this, dbManager);

        // Initialize visibility cache for thread-safe async operations
        this.visibilityCache = new VisibilityCache(this);

        // Register commands (can be done before data is loaded)
        registerCommands();

        // Register event listeners
        registerListeners();
        
        // Perform async initialization to prevent blocking the main thread
        getLogger().info("Starting async database initialization...");
        
        dbManager.initializeAsync()
                .thenCompose(v -> {
                    getLogger().info("Database initialized, loading location data...");
                    return crdStore.loadDataAsync();
                })
                .thenRun(() -> {
                    getLogger().info("Location data loaded, checking for YAML migration...");
                    // Perform migration from YAML to SQLite if needed
                    LocationRepository repository = new LocationRepository(dbManager);
                    DataMigration migration = new DataMigration(this, crdStore, repository);
                    migration.migrateIfNeeded();
                })
                .thenRun(() -> {
                    getLogger().info("TestPlugin initialization complete!");
                })
                .exceptionally(ex -> {
                    getLogger().severe("Failed to initialize TestPlugin: " + ex.getMessage());
                    ex.printStackTrace();
                    getLogger().severe("Plugin will be disabled.");

                    // Must return to main thread before disabling plugin
                    Bukkit.getScheduler().runTask(this, () -> {
                        getServer().getPluginManager().disablePlugin(this);
                    });

                    return null;
                });
    }
    
    /**
     * Registers all plugin commands.
     */
    private void registerCommands() {
        GotoCommand gotoCmd = new GotoCommand(crdStore, messages, visibilityCache);
        Objects.requireNonNull(this.getCommand("goto")).setExecutor(gotoCmd);
        Objects.requireNonNull(this.getCommand("goto")).setTabCompleter(gotoCmd);

        ListLocsCommand listLocsCmd = new ListLocsCommand(crdStore, messages);
        Objects.requireNonNull(this.getCommand("listlocs")).setExecutor(listLocsCmd);
        Objects.requireNonNull(this.getCommand("listlocs")).setTabCompleter(listLocsCmd);

        SetLocCommand setLocCmd = new SetLocCommand(crdStore, messages);
        Objects.requireNonNull(this.getCommand("setloc")).setExecutor(setLocCmd);
        Objects.requireNonNull(this.getCommand("setloc")).setTabCompleter(setLocCmd);

        StoreLocCommand storeLocCmd = new StoreLocCommand(crdStore, messages);
        Objects.requireNonNull(this.getCommand("storeloc")).setExecutor(storeLocCmd);
        Objects.requireNonNull(this.getCommand("storeloc")).setTabCompleter(storeLocCmd);

        DeleteLocCommand deleteLocCmd = new DeleteLocCommand(crdStore, messages);
        Objects.requireNonNull(this.getCommand("deleteloc")).setExecutor(deleteLocCmd);
        Objects.requireNonNull(this.getCommand("deleteloc")).setTabCompleter(deleteLocCmd);
    }
    
    /**
     * Registers all event listeners.
     */
    private void registerListeners() {
        this.getServer().getPluginManager().registerEvents(new TabCompletionInterceptor(crdStore, visibilityCache), this);
        this.getServer().getPluginManager().registerEvents(new CommandInterceptor(crdStore, messages), this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(crdStore, visibilityCache), this);
        this.getServer().getPluginManager().registerEvents(new PlayerQuitListener(visibilityCache), this);
    }

    @Override
    public void onDisable() {
        // Close database connection
        if (dbManager != null) {
            dbManager.close();
        }
    }
}