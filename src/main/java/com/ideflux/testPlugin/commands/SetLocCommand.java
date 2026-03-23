package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.MessageManager;
import org.bukkit.Location;
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

/**
 * This class defines the SetLocCommand, which allows players to overwrite the coordinates
 * of an existing location in the {@link CoordinateStore}.
 * The command ensures that the location must already exist in the storage to be modified.
 * Implements {@link CommandExecutor} to handle command execution and {@link TabCompleter}
 * for providing suggestions during command typing.
 */
public class SetLocCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;
    private final MessageManager messages;

    public SetLocCommand(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }

        // Require basic permission to modify locations
        if (!player.hasPermission("testplugin.basic")) {
            player.sendMessage(messages.getNoBasicPermission());
            return true;
        }

        if (args.length != 4) {
            player.sendMessage(messages.getUsageSetLoc());
            return true;
        }

        String targetName = args[0].toLowerCase();

        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);

            // Enforce ownership: only allow modifying locations owned by the player
            CoordinateStore.SavedLocation existing = crdStore.getPoint(player.getUniqueId(), targetName);
            if (existing == null) {
                player.sendMessage(messages.getLocationNotFound(targetName));
                return true;
            }

            // Update the location (owner can only modify their own locations)
            crdStore.storePoint(player.getUniqueId(), targetName, existing.worldName(), x, y, z);

            player.sendMessage(messages.getLocationUpdated(targetName));
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(messages.getInvalidCoordinates());
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // Suggest valid existing names owned by the player for the first argument
        if (args.length == 1) {
            for (String name : crdStore.getSavedNames(player.getUniqueId())) {
                if (name.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(name);
                }
            }
        }
        // Suggest current coordinates for the remaining arguments
        else if (args.length >= 2 && args.length <= 4) {
            Location loc = player.getLocation();
            if (args.length == 2) completions.add(String.valueOf(loc.getBlockX()));
            if (args.length == 3) completions.add(String.valueOf(loc.getBlockY()));
            if (args.length == 4) completions.add(String.valueOf(loc.getBlockZ()));
        }

        return completions;
    }
}