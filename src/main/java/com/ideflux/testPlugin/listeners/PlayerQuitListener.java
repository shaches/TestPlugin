package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.VisibilityCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener that updates the visibility cache when a player quits.
 * Maintains cache consistency by removing disconnected players.
 */
public class PlayerQuitListener implements Listener {

    private final VisibilityCache visibilityCache;

    public PlayerQuitListener(VisibilityCache visibilityCache) {
        this.visibilityCache = visibilityCache;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        visibilityCache.onPlayerQuit(event.getPlayer());
    }
}
