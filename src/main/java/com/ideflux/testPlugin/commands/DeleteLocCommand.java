package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Command implementation for deleting saved locations.
 * Players can delete their own saved locations to prevent file bloat over time.
 *
 * Usage: /deleteloc &lt;name&gt;
 */
public class DeleteLocCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;
    private final MessageManager messages;

    public DeleteLocCommand(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }

        // Require basic permission to delete locations
        if (!player.hasPermission("testplugin.basic")) {
            player.sendMessage(messages.getNoBasicPermission());
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(messages.getUsageDeleteLoc());
            return true;
        }

        String name = args[0].toLowerCase();
        
        // Check if the location exists
        if (crdStore.getPoint(player.getUniqueId(), name) == null) {
            player.sendMessage(messages.getLocationNotFound(name));
            return true;
        }

        // Delete the location
        crdStore.deletePoint(player.getUniqueId(), name);

        player.sendMessage(messages.getLocationDeleted(name));

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // Suggest the player's saved locations
            Set<String> savedNames = crdStore.getSavedNames(player.getUniqueId());
            List<String> completions = new ArrayList<>(savedNames);
            
            // Filter by what the player has typed so far
            String partial = args[0].toLowerCase();
            completions.removeIf(name -> !name.toLowerCase().startsWith(partial));
            
            return completions;
        }

        return Collections.emptyList();
    }
}
