package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The TabCompletionInterceptor class listens for asynchronous tab completion events and
 * modifies or appends auto-completion suggestions based on user input and saved data
 * stored in a {@code CoordinateStore}.
 *
 * Supported formats:
 * - #name          - Tab-completes the player's own location names
 * - #player:name   - Tab-completes another player's location names (read-only access)
 *
 * If the user provides a partial input that begins with "#", the class dynamically suggests
 * matching completions from both the player's own locations and other players' locations.
 */
public class TabCompletionInterceptor implements Listener {

    private final CoordinateStore crdStore;

    public TabCompletionInterceptor(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @EventHandler
    public void onAsyncTabComplete(AsyncTabCompleteEvent event) {
        if (!(event.getSender() instanceof Player player)) {
            return;
        }

        String buffer = event.getBuffer();
        String[] args = buffer.split(" ", -1); // -1 preserves trailing empty strings
        String lastArg = args[args.length - 1];

        if (lastArg.startsWith("#")) {
            String reference = lastArg.substring(1); // Remove '#' prefix
            List<String> newCompletions = new ArrayList<>(event.getCompletions());

            // Check if the reference contains a colon (cross-player reference)
            if (reference.contains(":")) {
                // Require permission to access other players' locations
                if (player.hasPermission("testplugin.others")) {
                    String[] refParts = reference.split(":", 2);
                    String targetPlayerName = refParts[0];
                    String partialLocationName = refParts.length > 1 ? refParts[1].toLowerCase() : "";

                    // Resolve the target player's UUID using safe cache lookup
                    UUID targetUUID = crdStore.resolvePlayerUUID(targetPlayerName);
                    if (targetUUID != null) {
                        // Suggest the target player's location names
                        for (String locName : crdStore.getSavedNames(targetUUID)) {
                            if (locName.toLowerCase().startsWith(partialLocationName)) {
                                newCompletions.add("#" + targetPlayerName + ":" + locName);
                            }
                        }
                    }
                }
            } else {
                String partialName = reference.toLowerCase();

                // Suggest the player's own location names (requires basic permission)
                if (player.hasPermission("testplugin.basic")) {
                    for (String name : crdStore.getSavedNames(player.getUniqueId())) {
                        if (name.toLowerCase().startsWith(partialName)) {
                            newCompletions.add("#" + name);
                        }
                    }
                }

                // Suggest online player names with colon suffix (requires others permission)
                if (player.hasPermission("testplugin.others")) {
                    for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                        // Skip if the requesting player cannot see the online player (vanish check)
                        if (!player.canSee(onlinePlayer) || onlinePlayer.equals(player)) {
                            continue;
                        }
                        if (!crdStore.getSavedNames(onlinePlayer.getUniqueId()).isEmpty()) {
                            String playerPrefix = onlinePlayer.getName().toLowerCase() + ":";
                            if (playerPrefix.startsWith(partialName)) {
                                newCompletions.add("#" + onlinePlayer.getName() + ":");
                            }
                        }
                    }
                }
            }

            event.setCompletions(newCompletions);
        }
    }
}