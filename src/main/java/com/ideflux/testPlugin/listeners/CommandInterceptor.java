package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class CommandInterceptor implements Listener {

    private final CoordinateStore crdStore;

    public CommandInterceptor(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage(); // E.g., "/tp @p #home"
        String[] parts = message.split(" ");
        boolean modified = false;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // Check if the argument starts with the designated trigger character
            if (part.startsWith("#") && part.length() > 1) {
                // Isolate the name by removing the '#' prefix
                String locName = part.substring(1).toLowerCase();
                double[] coords = crdStore.getPoint(locName);

                if (coords != null) {
                    // Replace the #name argument with floating-point X Y Z values
                    parts[i] = String.format("%.2f %.2f %.2f", coords[0], coords[1], coords[2]);
                    modified = true;
                }
            }
        }

        if (modified) {
            // Reconstruct the modified string and overwrite the pending command
            String newCommand = String.join(" ", parts);
            event.setMessage(newCommand);

            // Notify the player of the substitution
            Player player = event.getPlayer();
            player.sendMessage(Component.text("Parsed command: ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(newCommand).color(NamedTextColor.AQUA)));
        }
    }
}
