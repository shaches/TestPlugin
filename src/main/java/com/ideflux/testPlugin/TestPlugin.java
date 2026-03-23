package com.ideflux.testPlugin;

import com.ideflux.testPlugin.commands.DeleteLocCommand;
import com.ideflux.testPlugin.commands.GotoCommand;
import com.ideflux.testPlugin.commands.ListLocsCommand;
import com.ideflux.testPlugin.commands.SetLocCommand;
import com.ideflux.testPlugin.commands.StoreLocCommand;
import com.ideflux.testPlugin.listeners.CommandInterceptor;
import com.ideflux.testPlugin.listeners.PlayerJoinListener;
import com.ideflux.testPlugin.listeners.TabCompletionInterceptor;
import com.ideflux.testPlugin.storage.DataMigration;
import com.ideflux.testPlugin.storage.DatabaseManager;
import com.ideflux.testPlugin.storage.LocationRepository;
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

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        
        // Initialize message manager
        this.messages = new MessageManager(this);
        
        // Initialize database
        this.dbManager = new DatabaseManager(this);
        this.dbManager.initialize();
        
        // Initialize coordinate store with database
        this.crdStore = new CoordinateStore(this, dbManager);

        // Instantiate command classes
        GotoCommand gotoCmd = new GotoCommand(crdStore, messages);
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

        this.getServer().getPluginManager().registerEvents(new TabCompletionInterceptor(crdStore), this);
        this.getServer().getPluginManager().registerEvents(new CommandInterceptor(crdStore, messages), this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(crdStore), this);
        
        // Perform migration from YAML to SQLite if needed
        LocationRepository repository = new LocationRepository(dbManager);
        DataMigration migration = new DataMigration(this, crdStore, repository);
        migration.migrateIfNeeded();
    }

    @Override
    public void onDisable() {
        // Close database connection
        if (dbManager != null) {
            dbManager.close();
        }
    }
}