package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.VisibilityCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener that updates caches on player join.
 * This prevents blocking API calls by maintaining local caches of known players.
 */
public class PlayerJoinListener implements Listener {

    private final CoordinateStore crdStore;
    private final VisibilityCache visibilityCache;

    public PlayerJoinListener(CoordinateStore crdStore, VisibilityCache visibilityCache) {
        this.crdStore = crdStore;
        this.visibilityCache = visibilityCache;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        crdStore.updateUsernameCache(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        visibilityCache.onPlayerJoin(event.getPlayer());
    }
}
