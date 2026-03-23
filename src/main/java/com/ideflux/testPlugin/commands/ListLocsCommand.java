package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.MessageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
    private final MessageManager messages;

    public ListLocsCommand(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getPlayerOnly());
            return true;
        }

        UUID targetOwnerId;
        String targetPlayerName = null;

        // Determine whose locations to display
        if (args.length == 0) {
            // Show own locations - require basic permission
            if (!player.hasPermission("testplugin.basic")) {
                player.sendMessage(messages.getNoBasicPermission());
                return true;
            }

            targetOwnerId = player.getUniqueId();
        } else if (args.length == 1) {
            // Show another player's locations - require others permission
            if (!player.hasPermission("testplugin.others")) {
                player.sendMessage(messages.getNoOthersPermission());
                return true;
            }

            targetPlayerName = args[0];
            targetOwnerId = crdStore.resolvePlayerUUID(targetPlayerName);

            if (targetOwnerId == null) {
                player.sendMessage(messages.getPlayerNotFound(targetPlayerName));
                return true;
            }
        } else {
            player.sendMessage(messages.getUsageListLocs());
            return true;
        }

        Set<String> savedNames = crdStore.getSavedNames(targetOwnerId);

        if (savedNames.isEmpty()) {
            player.sendMessage(messages.getListEmpty());
            return true;
        }

        // Send header based on ownership
        if (targetPlayerName == null) {
            player.sendMessage(messages.getOwnListHeader());
        } else {
            player.sendMessage(messages.getOtherListHeader(targetPlayerName));
        }

        for (String name : savedNames) {
            CoordinateStore.SavedLocation c = crdStore.getPoint(targetOwnerId, name);

            // Construct the goto command based on ownership
            String gotoCommand;
            if (targetOwnerId.equals(player.getUniqueId())) {
                gotoCommand = "/goto " + name;
            } else {
                gotoCommand = "/goto " + targetPlayerName + ":" + name;
            }

            Component lineComponent = messages.getListEntry(name, c.worldName(), c.x(), c.y(), c.z())
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