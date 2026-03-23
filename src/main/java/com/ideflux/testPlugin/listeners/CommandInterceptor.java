package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.MessageManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Set;
import java.util.UUID;

/**
 * The CommandInterceptor class listens for player command preprocess events and modifies commands
 * that include specially formatted arguments referencing named coordinates stored in the CoordinateStore.
 *
 * Supported formats:
 * - #name          - References the player's own location
 * - #player:name   - References another player's location (read-only access)
 *
 * These arguments are replaced with the X, Y, Z floating-point coordinates of the corresponding
 * saved location, if found in the CoordinateStore.
 *
 * Only whitelisted commands are processed to prevent unintended replacements in chat or text-based commands.
 *
 * If the command is modified, the player is notified of the substitution in the chat.
 */
public class CommandInterceptor implements Listener {

    private final CoordinateStore crdStore;
    private final MessageManager messages;
    
    // Whitelist of commands that support coordinate substitution
    private static final Set<String> WHITELISTED_COMMANDS = Set.of(
            "/tp", "/teleport", "/minecraft:tp", "/minecraft:teleport",
            "/execute", "/minecraft:execute",
            "/setblock", "/minecraft:setblock",
            "/fill", "/minecraft:fill",
            "/clone", "/minecraft:clone",
            "/particle", "/minecraft:particle",
            "/summon", "/minecraft:summon",
            "/spawnpoint", "/minecraft:spawnpoint",
            "/setworldspawn", "/minecraft:setworldspawn",
            "/goto"
    );

    public CommandInterceptor(CoordinateStore crdStore, MessageManager messages) {
        this.crdStore = crdStore;
        this.messages = messages;
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage(); // E.g., "/tp @p #home" or "/tp @p #player:home"
        String[] parts = message.split(" ");
        
        // Check if command is whitelisted
        if (parts.length == 0) return;
        String command = parts[0].toLowerCase();
        if (!WHITELISTED_COMMANDS.contains(command)) {
            return; // Skip non-whitelisted commands
        }
        
        boolean modified = false;
        Player player = event.getPlayer();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];

            // Check if the argument starts with the designated trigger character
            if (part.startsWith("#") && part.length() > 1) {
                String reference = part.substring(1); // Remove '#' prefix
                UUID targetOwnerId;
                String locationName;

                // Check if the reference contains a colon (cross-player reference)
                if (reference.contains(":")) {
                    // Require permission to access other players' locations
                    if (!player.hasPermission("testplugin.others")) {
                        continue; // Skip if no permission
                    }

                    String[] refParts = reference.split(":", 2);
                    if (refParts.length != 2) continue;

                    String targetPlayerName = refParts[0];
                    locationName = refParts[1].toLowerCase();

                    // Resolve the target player's UUID using safe cache lookup
                    targetOwnerId = crdStore.resolvePlayerUUID(targetPlayerName);
                    if (targetOwnerId == null) {
                        continue; // Skip if player not found
                    }
                } else {
                    // Use the player's own locations - require basic permission
                    if (!player.hasPermission("testplugin.basic")) {
                        continue; // Skip if no permission
                    }

                    targetOwnerId = player.getUniqueId();
                    locationName = reference.toLowerCase();
                }

                // Retrieve the location
                CoordinateStore.SavedLocation coords = crdStore.getPoint(targetOwnerId, locationName);

                if (coords != null) {
                    // Replace the #name or #player:name argument with floating-point X Y Z values
                    parts[i] = String.format("%.2f %.2f %.2f",
                            coords.x(),
                            coords.y(),
                            coords.z());
                    modified = true;
                }
            }
        }

        if (modified) {
            // Reconstruct the modified string and overwrite the pending command
            String newCommand = String.join(" ", parts);
            event.setMessage(newCommand);

            // Notify the player of the substitution
            player.sendMessage(messages.getParsedCommand(newCommand));
        }
    }
}
