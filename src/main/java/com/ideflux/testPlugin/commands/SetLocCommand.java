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
import java.util.Collections;
import java.util.List;

public class SetLocCommand implements CommandExecutor, TabCompleter {

    private final CoordinateStore crdStore;

    public SetLocCommand(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (args.length != 4) {
            player.sendMessage(Component.text("Usage: /setloc <name> <x> <y> <z>").color(NamedTextColor.RED));
            return true;
        }

        String targetName = args[0].toLowerCase();

        try {
            double x = Double.parseDouble(args[1]);
            double y = Double.parseDouble(args[2]);
            double z = Double.parseDouble(args[3]);

            // Enforce overwrite-only logic: deny operation if the key does not exist
            if (crdStore.getPoint(targetName) == null) {
                player.sendMessage(Component.text("Error: Location '" + targetName + "' does not exist. Use /storeloc to create it.").color(NamedTextColor.RED));
                return true;
            }

            // HashMap automatically overwrites the value for an existing key
            crdStore.storePoint(targetName, new double[]{x, y, z});

            player.sendMessage(Component.text("Overwrote coordinates for '")
                    .color(NamedTextColor.GREEN)
                    .append(Component.text(targetName).color(NamedTextColor.WHITE))
                    .append(Component.text("'.")));
            return true;

        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Error: Coordinate arguments must be numbers.").color(NamedTextColor.RED));
            return true;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }

        List<String> completions = new ArrayList<>();

        // Suggest valid existing names for the first argument
        if (args.length == 1) {
            for (String name : crdStore.getSavedNames()) {
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