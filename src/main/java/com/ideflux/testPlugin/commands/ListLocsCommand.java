package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The ListLocsCommand class implements a command executor that lists saved
 * coordinates with support for viewing both personal and other players' locations.
 *
 * Syntax:
 * - /listlocs        - List own locations
 * - /listlocs [player] - List another player's locations (read-only access)
 *
 * Key Features:
 * - Displays a formatted list of saved locations with coordinates
 * - Interactive hover/click features for teleportation
 * - Read-only access to other players' locations
 */
public class ListLocsCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;

    public ListLocsCommand(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        UUID targetOwnerId;
        String displayName;

        // Determine whose locations to display
        if (args.length == 0) {
            // Show own locations - require basic permission
            if (!player.hasPermission("testplugin.basic")) {
                player.sendMessage(Component.text("Error: You don't have permission to use saved locations.")
                        .color(NamedTextColor.RED));
                return true;
            }

            targetOwnerId = player.getUniqueId();
            displayName = "Your";
        } else if (args.length == 1) {
            // Show another player's locations - require others permission
            if (!player.hasPermission("testplugin.others")) {
                player.sendMessage(Component.text("Error: You don't have permission to view other players' locations.")
                        .color(NamedTextColor.RED));
                return true;
            }

            String targetPlayerName = args[0];
            targetOwnerId = crdStore.resolvePlayerUUID(targetPlayerName);

            if (targetOwnerId == null) {
                player.sendMessage(Component.text("Error: Player '" + targetPlayerName + "' not found. Player must be online or have been seen before.").color(NamedTextColor.RED));
                return true;
            }

            displayName = targetPlayerName + "'s";
        } else {
            player.sendMessage(Component.text("Usage: /listlocs [player]").color(NamedTextColor.RED));
            return true;
        }

        Set<String> savedNames = crdStore.getSavedNames(targetOwnerId);

        if (savedNames.isEmpty()) {
            player.sendMessage(Component.text(displayName + " saved locations are empty.").color(NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("--- " + displayName + " Saved Coordinates ---")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        for (String name : savedNames) {
            CoordinateStore.SavedLocation c = crdStore.getPoint(targetOwnerId, name);
            String coordsText = String.format("%.1f, %.1f, %.1f", c.x(), c.y(), c.z());

            // Construct the goto command based on ownership
            String gotoCommand;
            if (targetOwnerId.equals(player.getUniqueId())) {
                gotoCommand = "/goto " + name;
            } else {
                gotoCommand = "/goto " + args[0] + ":" + name;
            }

            Component lineComponent = Component.text("[" + name + "] ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(coordsText).color(NamedTextColor.YELLOW))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to teleport to " + name).color(NamedTextColor.GREEN)
                    ))
                    .clickEvent(ClickEvent.runCommand(gotoCommand));

            player.sendMessage(lineComponent);
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }

        List<String> completions = new ArrayList<>();

        // Suggest online player names for the first argument (only if player has permission)
        if (args.length == 1 && ((Player) sender).hasPermission("testplugin.others")) {
            String partial = args[0].toLowerCase();
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                String playerName = onlinePlayer.getName();
                if (playerName.toLowerCase().startsWith(partial)) {
                    completions.add(playerName);
                }
            }
        }

        return completions;
    }
}