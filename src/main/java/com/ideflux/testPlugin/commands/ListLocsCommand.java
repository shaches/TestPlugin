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
            double[] c = crdStore.getPoint(name);
            String coordsText = String.format("%.1f, %.1f, %.1f", c[0], c[1], c[2]);

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