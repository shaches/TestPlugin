package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener that updates the username-to-UUID cache on player join.
 * This prevents blocking API calls by maintaining a local cache of known players.
 */
public class PlayerJoinListener implements Listener {

    private final CoordinateStore crdStore;

    public PlayerJoinListener(CoordinateStore crdStore) {
        this.crdStore = crdStore;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        crdStore.updateUsernameCache(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }
}
