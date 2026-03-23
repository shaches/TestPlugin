package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
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

/**
 * The GotoCommand class provides functionality for teleporting a player
 * to a saved location by name, using the "/goto" command.
 *
 * This command retrieves coordinates from the provided CoordinateStore
 * and teleports the player to the corresponding location in their current world.
 *
 * This class also implements tab completion for the command, suggesting saved
 * location names as the player types their command arguments.
 */
public class GotoCommand implements CommandExecutor, TabCompleter {

    // Dependency Injection: The command class stores a reference to the data store
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
            player.sendMessage(Component.text("Usage: /goto <name>").color(NamedTextColor.RED));
            return true;
        }

        String targetName = args[0];
        CoordinateStore.SavedLocation loc = crdStore.getPoint(targetName);
        if (loc == null) {
            player.sendMessage(Component.text("Error: Location '" + targetName + "' does not exist.").color(NamedTextColor.RED));
            return true;
        }
        Location location = new Location(player.getWorld(), loc.x(), loc.y(), loc.z());
        boolean success = player.teleport(location);
        
        if (success) {
            player.sendMessage(Component.text("Teleported to ").color(NamedTextColor.GREEN).append(Component.text(targetName).color(NamedTextColor.WHITE)));
        } else {
            player.sendMessage(Component.text("Error: Failed to teleport.").color(NamedTextColor.RED));
        }
        return true;
    }

    // --- Tab Completion Logic ---
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return null; // Return null to fall back to default completion or no completion
        }

        List<String> completions = new ArrayList<>();

        // We only want to suggest completions for the first argument (the index)
        if (args.length == 1) {
            String partialName = args[0].toLowerCase(Locale.ROOT);
            for (String name : crdStore.getSavedNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(partialName)) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}