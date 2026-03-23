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
        double[] cords = crdStore.getPoint(targetName);

        if (cords == null) {
            player.sendMessage(Component.text("Error: Location '" + targetName + "' does not exist.").color(NamedTextColor.RED));
            return true;
        }

        Location loc = new Location(player.getWorld(), cords[0], cords[1], cords[2]);
        player.teleport(loc);
        player.sendMessage(Component.text("Teleported to ").color(NamedTextColor.GREEN).append(Component.text(targetName).color(NamedTextColor.WHITE)));
        return true;
    }

    // --- Tab Completion Logic ---
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        // We only want to suggest completions for the first argument (the index)
        if (args.length == 1) {
            for (String name : crdStore.getSavedNames()) {
                if (name.toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(name);
                }
            }
        }
        return completions;
    }
}