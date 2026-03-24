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
 * Command implementation providing functionality to store player locations
 * with a custom name as key for future retrieval using {@link CoordinateStore}.
 * The class handles saving coordinates in the player's current world along with
 * input validation and feedback for both command execution and tab completion.
 *
 * The command accepts four arguments:
 * - A custom name to associate with the saved location.
 * - The x, y, and z coordinates of the location.
 *
 * The command is executable only by players, not by non-player command senders.
 * Tab completion is partially implemented, providing location-related suggestions.
 */
public class StoreLocCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;
    private final MessageManager messages;

    public StoreLocCommand(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }

        // Require basic permission to save locations
        if (!player.hasPermission("testplugin.basic")) {
            player.sendMessage(messages.getNoBasicPermission());
            return true;
        }

        // The argument count is increased to 4 to accommodate the string key
        if (args.length != 4) {
            player.sendMessage(messages.getUsageStoreLoc());
            return true;
        }

        try {
            String name = args[0];

            // Validate location name to prevent database issues
            if (!name.matches("^[a-zA-Z0-9_-]+$")) {
                player.sendMessage(messages.getInvalidName());
                return true;
            }

            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);

            // Validate coordinates to prevent DoS via NaN/Infinity
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                player.sendMessage(messages.getInvalidCoordinates());
                return true;
            }

            // Check quota limits (admins can bypass)
            boolean canBypass = player.hasPermission("testplugin.admin") &&
                               player.getServer().getPluginManager().getPlugin("TestPlugin").getConfig().getBoolean("permissions.admin-bypass-quota", true);

            if (!crdStore.canSaveLocation(player.getUniqueId(), name, canBypass)) {
                int current = crdStore.getLocationCount(player.getUniqueId());
                int max = crdStore.getMaxLocationsPerPlayer();
                player.sendMessage(messages.getQuotaExceeded(current, max));
                return true;
            }

            // Store the location under the player's UUID (ownership enforcement)
            crdStore.storePoint(player.getUniqueId(), name, player.getWorld().getName(), x, y, z);

            player.sendMessage(messages.getLocationSaved(name));
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
        Location loc = player.getLocation();

        // Shifts the coordinate suggestions by one index to account for the name argument
        if (args.length == 1) {
            // Returning an empty list for the first argument allows the player to type any custom name
            return Collections.emptyList();
        } else if (args.length == 2) {
            completions.add(String.valueOf(loc.getBlockX()));
        } else if (args.length == 3) {
            completions.add(String.valueOf(loc.getBlockY()));
        } else if (args.length == 4) {
            completions.add(String.valueOf(loc.getBlockZ()));
        }

        return completions;
    }
}
