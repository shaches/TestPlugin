package com.ideflux.testPlugin;

import com.ideflux.testPlugin.commands.GotoCommand;
import com.ideflux.testPlugin.commands.ListLocsCommand;
import com.ideflux.testPlugin.commands.SetLocCommand;
import com.ideflux.testPlugin.commands.StoreLocCommand;
import com.ideflux.testPlugin.listeners.CommandInterceptor;
import com.ideflux.testPlugin.listeners.TabCompletionInterceptor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public class TestPlugin extends JavaPlugin {

    private CoordinateStore crdStore;

    @Override
    public void onEnable() {
        this.getServer().getPluginManager().registerEvents(new TabCompletionInterceptor(crdStore), this);
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

        this.getServer().getPluginManager().registerEvents(new CommandInterceptor(crdStore), this);
    }

    @Override
    public void onDisable() {
        if (crdStore != null) {
            crdStore.saveData();
        }
    }
}