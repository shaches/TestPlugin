package com.ideflux.testPlugin;

import com.ideflux.testPlugin.commands.DeleteLocCommand;
import com.ideflux.testPlugin.commands.GotoCommand;
import com.ideflux.testPlugin.commands.ListLocsCommand;
import com.ideflux.testPlugin.commands.SetLocCommand;
import com.ideflux.testPlugin.commands.StoreLocCommand;
import com.ideflux.testPlugin.listeners.CommandInterceptor;
import com.ideflux.testPlugin.listeners.PlayerJoinListener;
import com.ideflux.testPlugin.listeners.TabCompletionInterceptor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/**
 * The TestPlugin class is the main entry point for the plugin.
 * It handles the initialization and shutdown processes, including the registration of commands and events.
 *
 * Functionalities include:
 * - Loading and saving default configuration files.
 * - Instantiation of the CoordinateStore for managing saved locations.
 * - Registering commands such as:
 *   - `/goto`: Teleports players to saved locations.
 *   - `/listlocs`: Lists all saved location names.
 *   - `/setloc`: Updates or creates saved locations.
 *   - `/storeloc`: Adds a new saved location.
 * - Setting up event listeners for tab completion and command interception.
 *
 * This plugin integrates with the Spigot API and utilizes data persisted in the configuration file.
 */
public class TestPlugin extends JavaPlugin {

    private CoordinateStore crdStore;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.crdStore = new CoordinateStore(this);

        // Instantiate command classes
        GotoCommand gotoCmd = new GotoCommand(crdStore);
        Objects.requireNonNull(this.getCommand("goto")).setExecutor(gotoCmd);
        Objects.requireNonNull(this.getCommand("goto")).setTabCompleter(gotoCmd);

        ListLocsCommand listLocsCmd = new ListLocsCommand(crdStore);
        Objects.requireNonNull(this.getCommand("listlocs")).setExecutor(listLocsCmd);

        SetLocCommand setLocCmd = new SetLocCommand(crdStore);
        Objects.requireNonNull(this.getCommand("setloc")).setExecutor(setLocCmd);
        Objects.requireNonNull(this.getCommand("setloc")).setTabCompleter(setLocCmd);

        StoreLocCommand storeLocCmd = new StoreLocCommand(crdStore);
        Objects.requireNonNull(this.getCommand("storeloc")).setExecutor(storeLocCmd);
        Objects.requireNonNull(this.getCommand("storeloc")).setTabCompleter(storeLocCmd);

        DeleteLocCommand deleteLocCmd = new DeleteLocCommand(crdStore);
        Objects.requireNonNull(this.getCommand("deleteloc")).setExecutor(deleteLocCmd);
        Objects.requireNonNull(this.getCommand("deleteloc")).setTabCompleter(deleteLocCmd);

        this.getServer().getPluginManager().registerEvents(new TabCompletionInterceptor(crdStore), this);
        this.getServer().getPluginManager().registerEvents(new CommandInterceptor(crdStore), this);
        this.getServer().getPluginManager().registerEvents(new PlayerJoinListener(crdStore), this);
    }

    @Override
    public void onDisable() {
        if (crdStore != null) {
            crdStore.saveData();
        }
    }
}