package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
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

    public GotoCommand(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be run by a player.").color(NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /goto <name> or /goto <player>:<name>").color(NamedTextColor.RED));
            return true;
        }

        String input = args[0];
        UUID targetOwnerId;
        String locationName;

        // Check if the input contains a colon (cross-player reference)
        if (input.contains(":")) {
            String[] parts = input.split(":", 2);
            if (parts.length != 2) {
                player.sendMessage(Component.text("Error: Invalid format. Use <player>:<name>").color(NamedTextColor.RED));
                return true;
            }

            String targetPlayerName = parts[0];
            locationName = parts[1];

            // Resolve the target player's UUID
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
                player.sendMessage(Component.text("Error: Player '" + targetPlayerName + "' not found.").color(NamedTextColor.RED));
                return true;
            }
            targetOwnerId = targetPlayer.getUniqueId();
        } else {
            // Use the player's own locations
            targetOwnerId = player.getUniqueId();
            locationName = input;
        }

        // Retrieve the location (read-only access)
        CoordinateStore.SavedLocation loc = crdStore.getPoint(targetOwnerId, locationName);
        if (loc == null) {
            player.sendMessage(Component.text("Error: Location '" + locationName + "' does not exist.").color(NamedTextColor.RED));
            return true;
        }

        // Retrieve the target world
        World targetWorld = Bukkit.getWorld(loc.worldName());
        if (targetWorld == null) {
            player.sendMessage(Component.text("Error: World '" + loc.worldName() + "' not found.").color(NamedTextColor.RED));
            return true;
        }

        Location location = new Location(targetWorld, loc.x(), loc.y(), loc.z());
        boolean success = player.teleport(location);

        if (success) {
            player.sendMessage(Component.text("Teleported to ").color(NamedTextColor.GREEN)
                    .append(Component.text(locationName).color(NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("Error: Failed to teleport.").color(NamedTextColor.RED));
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

            // Suggest own locations
            for (String name : crdStore.getSavedNames(player.getUniqueId())) {
                if (name.toLowerCase(Locale.ROOT).startsWith(partialInput)) {
                    completions.add(name);
                }
            }

            // Suggest cross-player references (player:location)
            if (partialInput.contains(":")) {
                String[] parts = partialInput.split(":", 2);
                String targetPlayerName = parts[0];
                String partialLocationName = parts.length > 1 ? parts[1] : "";

                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName);
                if (targetPlayer.hasPlayedBefore() || targetPlayer.isOnline()) {
                    for (String locName : crdStore.getSavedNames(targetPlayer.getUniqueId())) {
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
        return completions;
    }
}