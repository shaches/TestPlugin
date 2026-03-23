package com.ideflux.testPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Manages localized messages from messages.yml configuration file.
 * Supports color codes using & symbol and placeholder replacement.
 * Thread-safe and allows server administrators to customize all user-facing messages.
 */
public class MessageManager {
    
    private final JavaPlugin plugin;
    private FileConfiguration messagesConfig;
    private final LegacyComponentSerializer serializer;
    
    public MessageManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.serializer = LegacyComponentSerializer.legacyAmpersand();
        loadMessages();
    }
    
    /**
     * Loads or creates the messages.yml file.
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        
        // Create messages.yml if it doesn't exist
        if (!messagesFile.exists()) {
            try {
                if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                    plugin.getLogger().warning("Failed to create plugin data folder");
                }
                InputStream defaultMessages = plugin.getResource("messages.yml");
                if (defaultMessages != null) {
                    Files.copy(defaultMessages, messagesFile.toPath());
                    plugin.getLogger().info("Created default messages.yml");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create messages.yml", e);
            }
        }
        
        // Load the configuration
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Loaded messages.yml");
    }
    
    /**
     * Reloads the messages configuration from disk.
     * Useful for live updates without restarting the server.
     * This method is intentionally kept for future use (e.g., /reload command).
     */
    @SuppressWarnings("unused")
    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Reloaded messages.yml");
    }
    
    /**
     * Gets a raw message string from the config (without color processing).
     */
    private String getRawMessage(String path) {
        return messagesConfig.getString(path, "&cMessage not found: " + path);
    }
    
    /**
     * Gets a message as a Component with color codes processed.
     * No placeholder replacement.
     */
    public Component getMessage(String path) {
        String raw = getRawMessage(path);
        return serializer.deserialize(raw);
    }
    
    /**
     * Gets a message as a Component with placeholder replacement.
     * 
     * @param path The message path in messages.yml (e.g., "storage.saved")
     * @param placeholders Map of placeholder names to values (e.g., "name" -> "home")
     * @return Formatted Component with colors and placeholders applied
     */
    public Component getMessage(String path, Map<String, String> placeholders) {
        String raw = getRawMessage(path);
        
        // Replace placeholders
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return serializer.deserialize(raw);
    }
    
    /**
     * Gets a message with a single placeholder.
     * Convenience method for common single-placeholder messages.
     */
    public Component getMessage(String path, String placeholderKey, String placeholderValue) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholderKey, placeholderValue);
        return getMessage(path, placeholders);
    }
    
    /**
     * Gets a console message (no color processing, plain text).
     * Used for logging messages.
     */
    public String getConsoleMessage(String path) {
        return messagesConfig.getString(path, "Message not found: " + path);
    }
    
    /**
     * Gets a console message with placeholder replacement.
     * Reserved for future database logging features.
     */
    @SuppressWarnings("unused")
    public String getConsoleMessage(String path, Map<String, String> placeholders) {
        String raw = getConsoleMessage(path);
        
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return raw;
    }
    
    // ==================== Convenience Methods ====================
    // These methods provide quick access to commonly used messages
    
    public Component getPlayerOnly() {
        return getMessage("general.player-only");
    }
    
    @SuppressWarnings("unused")
    public Component getNoPermission() {
        return getMessage("general.no-permission");
    }
    
    public Component getNoBasicPermission() {
        return getMessage("permissions.no-basic");
    }
    
    public Component getNoOthersPermission() {
        return getMessage("permissions.no-others");
    }
    
    public Component getLocationSaved(String name) {
        return getMessage("storage.saved", "name", name);
    }
    
    public Component getLocationUpdated(String name) {
        return getMessage("storage.updated", "name", name);
    }
    
    public Component getLocationDeleted(String name) {
        return getMessage("storage.deleted", "name", name);
    }
    
    public Component getLocationNotFound(String name) {
        return getMessage("storage.not-found", "name", name);
    }
    
    public Component getPlayerNotFound(String player) {
        return getMessage("storage.player-not-found", "player", player);
    }
    
    public Component getInvalidName() {
        return getMessage("storage.invalid-name");
    }
    
    public Component getTeleportSuccess(String name) {
        return getMessage("teleport.success", "name", name);
    }
    
    public Component getCrossPlayerTeleportSuccess(String owner, String name) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("owner", owner);
        placeholders.put("name", name);
        return getMessage("teleport.cross-player-success", placeholders);
    }
    
    public Component getTeleportFailed() {
        return getMessage("teleport.failed");
    }
    
    @SuppressWarnings("unused")
    public Component getListHeader() {
        return getMessage("list.header");
    }
    
    public Component getOwnListHeader() {
        return getMessage("list.own-header");
    }
    
    public Component getOtherListHeader(String player) {
        return getMessage("list.other-header", "player", player);
    }
    
    public Component getListEntry(String name, String world, double x, double y, double z) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", name);
        placeholders.put("world", world);
        placeholders.put("x", String.format("%.2f", x));
        placeholders.put("y", String.format("%.2f", y));
        placeholders.put("z", String.format("%.2f", z));
        return getMessage("list.entry", placeholders);
    }
    
    public Component getListEmpty() {
        return getMessage("list.empty");
    }
    
    public Component getUsageGoto() {
        return getMessage("usage.goto");
    }
    
    public Component getUsageStoreLoc() {
        return getMessage("usage.storeloc");
    }
    
    public Component getUsageSetLoc() {
        return getMessage("usage.setloc");
    }
    
    public Component getUsageDeleteLoc() {
        return getMessage("usage.deleteloc");
    }
    
    public Component getUsageListLocs() {
        return getMessage("usage.listlocs");
    }
    
    public Component getParsedCommand(String command) {
        return getMessage("interceptor.parsed-command", "command", command);
    }
    
    public Component getInvalidCoordinates() {
        return getMessage("errors.invalid-coordinates");
    }
    
    @SuppressWarnings("unused")
    public Component getDatabaseError() {
        return getMessage("errors.database-error");
    }
}
