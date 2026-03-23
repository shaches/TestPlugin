package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The ListLocsCommand class implements a command executor that lists all saved
 * coordinates stored within a {@link CoordinateStore}. It provides players with a formatted
 * list of saved locations and interactive options to teleport to specific coordinates.
 *
 * This command is useful for managing and accessing previously saved locations in the game.
 * The saved locations are displayed with their names, coordinates, and hover/click features
 * for teleportation.
 *
 * Constructor:
 * - {@code ListLocsCommand(CoordinateStore crdStore)}: Initializes the command with a given
 *   {@link CoordinateStore} instance to manage saved locations.
 *
 * Key Features:
 * - Only accessible to player senders; non-player senders are ignored.
 * - Displays a message if no saved coordinates are available.
 * - Lists all saved coordinate names and their respective (x, y, z) values.
 * - Includes hoverable text for additional information and clickable text to teleport to
 *   the specified coordinates.
 *
 * Implementation Details:
 * - Utilizes Adventure components to format and interact with the displayed message.
 * - Retrieves saved names and coordinates from the provided {@link CoordinateStore}.
 */
public class ListLocsCommand implements CommandExecutor {

    private final CoordinateStore crdStore;

    public ListLocsCommand(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        Set<String> savedNames = crdStore.getSavedNames();

        if (savedNames.isEmpty()) {
            player.sendMessage(Component.text("No coordinates have been saved yet.").color(NamedTextColor.RED));
            return true;
        }

        player.sendMessage(Component.text("--- Saved Coordinates ---")
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));

        for (String name : savedNames) {
            CoordinateStore.SavedLocation c = crdStore.getPoint(name);
            String coordsText = String.format("%.1f, %.1f, %.1f", c.x(), c.y(), c.z());

            Component lineComponent = Component.text("[" + name + "] ")
                    .color(NamedTextColor.AQUA)
                    .append(Component.text(coordsText).color(NamedTextColor.YELLOW))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to teleport to " + name).color(NamedTextColor.GREEN)
                    ))
                    .clickEvent(ClickEvent.runCommand("/goto " + name));

            player.sendMessage(lineComponent);
        }

        return true;
    }
}