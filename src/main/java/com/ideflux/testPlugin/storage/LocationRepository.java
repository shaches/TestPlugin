package com.ideflux.testPlugin.storage;

import com.ideflux.testPlugin.CoordinateStore.SavedLocation;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for managing player locations in SQLite database.
 * Provides async CRUD operations with proper SQL injection protection.
 * All database operations are performed asynchronously to prevent main thread blocking.
 */
public class LocationRepository {
    
    private final DatabaseManager dbManager;
    
    public LocationRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }
    
    /**
     * Inserts or updates a location for a player.
     * Uses UPSERT (INSERT OR REPLACE) to handle duplicates.
     */
    public CompletableFuture<Void> saveLocation(UUID ownerId, String name, String worldName, double x, double y, double z) {
        String sql = """
            INSERT INTO player_locations (owner_uuid, location_name, world_name, x, y, z, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, strftime('%s', 'now'))
            ON CONFLICT(owner_uuid, location_name) 
            DO UPDATE SET 
                world_name = excluded.world_name,
                x = excluded.x,
                y = excluded.y,
                z = excluded.z,
                updated_at = strftime('%s', 'now')
            """;
        
        return dbManager.executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerId.toString());
                stmt.setString(2, name.toLowerCase());
                stmt.setString(3, worldName);
                stmt.setDouble(4, x);
                stmt.setDouble(5, y);
                stmt.setDouble(6, z);
                stmt.executeUpdate();
            }
            return null;
        });
    }
    
    /**
     * Retrieves a specific location for a player.
     * Returns null if not found.
     */
    public CompletableFuture<SavedLocation> getLocation(UUID ownerId, String name) {
        String sql = """
            SELECT world_name, x, y, z 
            FROM player_locations 
            WHERE owner_uuid = ? AND location_name = ?
            """;
        
        return dbManager.executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerId.toString());
                stmt.setString(2, name.toLowerCase());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new SavedLocation(
                            rs.getString("world_name"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")
                        );
                    }
                }
            }
            return null;
        });
    }
    
    /**
     * Retrieves all location names for a specific player.
     */
    public CompletableFuture<Set<String>> getLocationNames(UUID ownerId) {
        String sql = """
            SELECT location_name 
            FROM player_locations 
            WHERE owner_uuid = ?
            ORDER BY location_name
            """;
        
        return dbManager.executeAsync(conn -> {
            Set<String> names = new HashSet<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        names.add(rs.getString("location_name"));
                    }
                }
            }
            return names;
        });
    }
    
    /**
     * Retrieves all locations for a specific player.
     */
    public CompletableFuture<Map<String, SavedLocation>> getAllLocations(UUID ownerId) {
        String sql = """
            SELECT location_name, world_name, x, y, z 
            FROM player_locations 
            WHERE owner_uuid = ?
            """;
        
        return dbManager.executeAsync(conn -> {
            Map<String, SavedLocation> locations = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerId.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("location_name");
                        SavedLocation loc = new SavedLocation(
                            rs.getString("world_name"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z")
                        );
                        locations.put(name, loc);
                    }
                }
            }
            return locations;
        });
    }
    
    /**
     * Deletes a specific location for a player.
     * Returns true if deleted, false if not found.
     */
    public CompletableFuture<Boolean> deleteLocation(UUID ownerId, String name) {
        String sql = """
            DELETE FROM player_locations 
            WHERE owner_uuid = ? AND location_name = ?
            """;
        
        return dbManager.executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerId.toString());
                stmt.setString(2, name.toLowerCase());
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            }
        });
    }
    
    /**
     * Gets all player UUIDs that have saved locations.
     */
    public CompletableFuture<Set<UUID>> getAllOwners() {
        String sql = """
            SELECT DISTINCT owner_uuid 
            FROM player_locations
            """;
        
        return dbManager.executeAsync(conn -> {
            Set<UUID> owners = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    try {
                        owners.add(UUID.fromString(rs.getString("owner_uuid")));
                    } catch (IllegalArgumentException e) {
                        // Skip invalid UUIDs
                    }
                }
            }
            return owners;
        });
    }
    
    /**
     * Updates the username cache.
     */
    public CompletableFuture<Void> updateUsernameCache(String username, UUID uuid) {
        String sql = """
            INSERT INTO username_cache (username, uuid, last_seen)
            VALUES (?, ?, strftime('%s', 'now'))
            ON CONFLICT(username) 
            DO UPDATE SET 
                uuid = excluded.uuid,
                last_seen = strftime('%s', 'now')
            """;
        
        return dbManager.executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            }
            return null;
        });
    }
    
    /**
     * Retrieves UUID from username cache.
     * Returns null if not found.
     */
    public CompletableFuture<UUID> getUuidFromCache(String username) {
        String sql = """
            SELECT uuid 
            FROM username_cache 
            WHERE username = ?
            """;
        
        return dbManager.executeAsync(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username.toLowerCase());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        try {
                            return UUID.fromString(rs.getString("uuid"));
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    }
                }
            }
            return null;
        });
    }
    
    /**
     * Batch imports locations from a map structure.
     * Used for migrating from YAML to SQLite.
     */
    public CompletableFuture<Void> batchImportLocations(Map<UUID, Map<String, SavedLocation>> data) {
        String sql = """
            INSERT OR REPLACE INTO player_locations 
            (owner_uuid, location_name, world_name, x, y, z)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        return dbManager.executeAsync(conn -> {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Map.Entry<UUID, Map<String, SavedLocation>> playerEntry : data.entrySet()) {
                    String ownerId = playerEntry.getKey().toString();
                    
                    for (Map.Entry<String, SavedLocation> locEntry : playerEntry.getValue().entrySet()) {
                        String name = locEntry.getKey();
                        SavedLocation loc = locEntry.getValue();
                        
                        stmt.setString(1, ownerId);
                        stmt.setString(2, name);
                        stmt.setString(3, loc.worldName());
                        stmt.setDouble(4, loc.x());
                        stmt.setDouble(5, loc.y());
                        stmt.setDouble(6, loc.z());
                        stmt.addBatch();
                    }
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
            return null;
        });
    }
}
