package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The GotoCommand class provides functionality for teleporting a player
 * to a saved location, supporting both personal and cross-player access.
 *
 * Syntax:
 * - /goto <name>          - Teleport to own location
 * - /goto <player>:<name> - Teleport to another player's location (read-only access)
 *
 * This class implements read-only public access to other players' locations
 * while maintaining full access to the player's own locations.
 */
public class GotoCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;
    private final MessageManager messages;

    public GotoCommand(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(messages.getUsageGoto());
            return true;
        }

        String input = args[0];
        UUID targetOwnerId;
        String locationName;
        String ownerName = null;

        // Check if the input contains a colon (cross-player reference)
        if (input.contains(":")) {
            // Require permission to access other players' locations
            if (!player.hasPermission("testplugin.others")) {
                player.sendMessage(messages.getNoOthersPermission());
                return true;
            }

            String[] parts = input.split(":", 2);
            if (parts.length != 2) {
                player.sendMessage(messages.getUsageGoto());
                return true;
            }

            String targetPlayerName = parts[0];
            locationName = parts[1];
            ownerName = targetPlayerName;

            // Resolve the target player's UUID using safe cache lookup
            targetOwnerId = crdStore.resolvePlayerUUID(targetPlayerName);
            if (targetOwnerId == null) {
                player.sendMessage(messages.getPlayerNotFound(targetPlayerName));
                return true;
            }
        } else {
            // Use the player's own locations - require basic permission
            if (!player.hasPermission("testplugin.basic")) {
                player.sendMessage(messages.getNoBasicPermission());
                return true;
            }

            targetOwnerId = player.getUniqueId();
            locationName = input;
        }

        // Retrieve the location (read-only access)
        CoordinateStore.SavedLocation loc = crdStore.getPoint(targetOwnerId, locationName);
        if (loc == null) {
            player.sendMessage(messages.getLocationNotFound(locationName));
            return true;
        }

        // Retrieve the target world
        World targetWorld = Bukkit.getWorld(loc.worldName());
        if (targetWorld == null) {
            player.sendMessage(messages.getTeleportFailed());
            return true;
        }

        Location location = new Location(targetWorld, loc.x(), loc.y(), loc.z());
        boolean success = player.teleport(location);

        if (success) {
            if (ownerName != null) {
                player.sendMessage(messages.getCrossPlayerTeleportSuccess(ownerName, locationName));
            } else {
                player.sendMessage(messages.getTeleportSuccess(locationName));
            }
        } else {
            player.sendMessage(messages.getTeleportFailed());
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partialInput = args[0].toLowerCase(Locale.ROOT);

            // Suggest own locations (requires basic permission)
            if (player.hasPermission("testplugin.basic")) {
                for (String name : crdStore.getSavedNames(player.getUniqueId())) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(partialInput)) {
                        completions.add(name);
                    }
                }
            }

            // Suggest cross-player references only if player has permission
            if (player.hasPermission("testplugin.others")) {
                if (partialInput.contains(":")) {
                    String[] parts = partialInput.split(":", 2);
                    String targetPlayerName = parts[0];
                    String partialLocationName = parts.length > 1 ? parts[1] : "";

                    UUID targetUUID = crdStore.resolvePlayerUUID(targetPlayerName);
                    if (targetUUID != null) {
                        for (String locName : crdStore.getSavedNames(targetUUID)) {
                            if (locName.toLowerCase(Locale.ROOT).startsWith(partialLocationName)) {
                                completions.add(targetPlayerName + ":" + locName);
                            }
                        }
                    }
                } else {
                    // Suggest online player names with colon suffix
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        if (!onlinePlayer.equals(player) && !crdStore.getSavedNames(onlinePlayer.getUniqueId()).isEmpty()) {
                            String playerPrefix = onlinePlayer.getName().toLowerCase(Locale.ROOT) + ":";
                            if (playerPrefix.startsWith(partialInput)) {
                                completions.add(onlinePlayer.getName() + ":");
                            }
                        }
                    }
                }
            }
        }
        return completions;
    }
}