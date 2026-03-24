package com.ideflux.testPlugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for player visibility information.
 * This cache is maintained on the main server thread and can be safely read from async contexts.
 *
 * Prevents ConcurrentModificationException when accessing player visibility from async events
 * like AsyncTabCompleteEvent.
 */
public class VisibilityCache {

    private final JavaPlugin plugin;

    // Map of observer UUID -> Set of UUIDs they can see
    private final Map<UUID, Set<UUID>> visibilityMap = new ConcurrentHashMap<>();

    // Set of all online player UUIDs
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public VisibilityCache(JavaPlugin plugin) {
        this.plugin = plugin;

        // Schedule periodic updates on the main thread
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateCache, 0L, 20L); // Update every second
    }

    /**
     * Updates the visibility cache. MUST be called on the main thread.
     * This is scheduled to run periodically.
     */
    private void updateCache() {
        if (!Bukkit.isPrimaryThread()) {
            plugin.getLogger().warning("VisibilityCache.updateCache() called from non-main thread!");
            return;
        }

        Set<UUID> currentOnline = new HashSet<>();
        Map<UUID, Set<UUID>> newVisibilityMap = new HashMap<>();

        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        // Build visibility map
        for (Player observer : players) {
            UUID observerUuid = observer.getUniqueId();
            currentOnline.add(observerUuid);

            Set<UUID> visiblePlayers = new HashSet<>();
            for (Player target : players) {
                if (!observer.equals(target) && observer.canSee(target)) {
                    visiblePlayers.add(target.getUniqueId());
                }
            }

            newVisibilityMap.put(observerUuid, visiblePlayers);
        }

        // Update atomic references
        onlinePlayers.clear();
        onlinePlayers.addAll(currentOnline);

        visibilityMap.clear();
        visibilityMap.putAll(newVisibilityMap);
    }

    /**
     * Manually updates cache when a player joins. Should be called on main thread.
     */
    public void onPlayerJoin(Player player) {
        if (Bukkit.isPrimaryThread()) {
            updateCache();
        } else {
            Bukkit.getScheduler().runTask(plugin, this::updateCache);
        }
    }

    /**
     * Manually updates cache when a player quits. Should be called on main thread.
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        onlinePlayers.remove(uuid);
        visibilityMap.remove(uuid);

        // Remove from all other players' visibility sets
        for (Set<UUID> visibleSet : visibilityMap.values()) {
            visibleSet.remove(uuid);
        }
    }

    /**
     * Checks if the observer can see the target player.
     * Thread-safe: Can be called from async contexts.
     *
     * @param observer The player doing the observing
     * @param target The player being observed
     * @return true if observer can see target, false otherwise
     */
    public boolean canSee(UUID observer, UUID target) {
        if (observer.equals(target)) {
            return false; // Player can't see themselves in this context
        }

        Set<UUID> visiblePlayers = visibilityMap.get(observer);
        if (visiblePlayers == null) {
            return false;
        }

        return visiblePlayers.contains(target);
    }

    /**
     * Gets all online players that the observer can see.
     * Thread-safe: Can be called from async contexts.
     *
     * @param observer The player doing the observing
     * @return Immutable set of visible player UUIDs
     */
    public Set<UUID> getVisiblePlayers(UUID observer) {
        Set<UUID> visible = visibilityMap.get(observer);
        return visible != null ? Set.copyOf(visible) : Collections.emptySet();
    }

    /**
     * Checks if a player is currently online.
     * Thread-safe: Can be called from async contexts.
     */
    public boolean isOnline(UUID playerUuid) {
        return onlinePlayers.contains(playerUuid);
    }

    /**
     * Gets all online player UUIDs.
     * Thread-safe: Can be called from async contexts.
     */
    public Set<UUID> getOnlinePlayers() {
        return Set.copyOf(onlinePlayers);
    }
}
