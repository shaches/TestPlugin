package com.ideflux.testPlugin.commands;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import com.ideflux.testPlugin.MessageManager;
import com.ideflux.testPlugin.VisibilityCache;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for all command classes, covering:
 * - Permission enforcement (testplugin.basic, testplugin.others, testplugin.admin)
 * - Input validation (NaN, Infinity, name length, alphanumeric validation)
 * - Target resolution (cross-player location syntax player:name vs personal locations)
 */
class CommandTestSuite {

    @Mock
    private CoordinateStore mockStore;

    @Mock
    private MessageManager mockMessages;

    @Mock
    private VisibilityCache mockVisibilityCache;

    @Mock
    private Player mockPlayer;

    @Mock
    private Command mockCommand;

    @Mock
    private World mockWorld;

    @Mock
    private Server mockServer;

    private MockedStatic<Bukkit> bukkit;
    private AutoCloseable mocks;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        playerUuid = UUID.randomUUID();

        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");

        // Mock message manager to return components
        when(mockMessages.getPlayerOnly()).thenReturn(Component.text("Player only"));
        when(mockMessages.getNoBasicPermission()).thenReturn(Component.text("No basic permission"));
        when(mockMessages.getNoOthersPermission()).thenReturn(Component.text("No others permission"));
        when(mockMessages.getUsageStoreLoc()).thenReturn(Component.text("Usage: /storeloc"));
        when(mockMessages.getUsageSetLoc()).thenReturn(Component.text("Usage: /setloc"));
        when(mockMessages.getUsageDeleteLoc()).thenReturn(Component.text("Usage: /deleteloc"));
        when(mockMessages.getUsageGoto()).thenReturn(Component.text("Usage: /goto"));
        when(mockMessages.getUsageListLocs()).thenReturn(Component.text("Usage: /listlocs"));
        when(mockMessages.getInvalidName()).thenReturn(Component.text("Invalid name"));
        when(mockMessages.getNameTooLong(anyInt())).thenReturn(Component.text("Name too long"));
        when(mockMessages.getInvalidCoordinates()).thenReturn(Component.text("Invalid coordinates"));
        when(mockMessages.getLocationSaved(anyString())).thenReturn(Component.text("Location saved"));
        when(mockMessages.getLocationUpdated(anyString())).thenReturn(Component.text("Location updated"));
        when(mockMessages.getLocationDeleted(anyString())).thenReturn(Component.text("Location deleted"));
        when(mockMessages.getLocationNotFound(anyString())).thenReturn(Component.text("Location not found"));
        when(mockMessages.getPlayerNotFound(anyString())).thenReturn(Component.text("Player not found"));
        when(mockMessages.getQuotaExceeded(anyInt(), anyInt())).thenReturn(Component.text("Quota exceeded"));
        when(mockMessages.getTeleportSuccess(anyString())).thenReturn(Component.text("Teleport success"));
        when(mockMessages.getCrossPlayerTeleportSuccess(anyString(), anyString())).thenReturn(Component.text("Cross-player teleport"));

        // Mock Bukkit
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(mockServer);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bukkit != null) {
            bukkit.close();
        }
        mocks.close();
    }

    // ==================== StoreLocCommand Tests ====================

    @Nested
    @DisplayName("StoreLocCommand Tests")
    class StoreLocCommandTests {

        private StoreLocCommand command;

        @BeforeEach
        void setUp() {
            command = new StoreLocCommand(mockStore, mockMessages);
        }

        @Test
        @DisplayName("Permission: Requires testplugin.basic permission")
        void testStoreLocRequiresBasicPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoBasicPermission());
            verify(mockStore, never()).storePoint(any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Input Validation: Rejects NaN coordinates")
        void testStoreLocRejectsNaN() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.canSaveLocation(any(), any(), anyBoolean())).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home", "NaN", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getInvalidCoordinates());
            verify(mockStore, never()).storePoint(any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Input Validation: Rejects Infinity coordinates")
        void testStoreLocRejectsInfinity() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.canSaveLocation(any(), any(), anyBoolean())).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home", "Infinity", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getInvalidCoordinates());
        }

        @Test
        @DisplayName("Input Validation: Rejects names exceeding 32 characters")
        void testStoreLocRejectsLongNames() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            String longName = "a".repeat(33);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{longName, "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNameTooLong(32));
            verify(mockStore, never()).storePoint(any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Input Validation: Rejects non-alphanumeric names")
        void testStoreLocRejectsInvalidCharacters() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home@location", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getInvalidName());
            verify(mockStore, never()).storePoint(any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Input Validation: Accepts valid alphanumeric names with underscores and hyphens")
        void testStoreLocAcceptsValidNames() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.canSaveLocation(any(), any(), anyBoolean())).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home_base-1", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockStore).storePoint(playerUuid, "home_base-1", "world", 0.0, 64.0, 0.0);
        }

        @Test
        @DisplayName("Quota: Enforces per-player quota limits")
        void testStoreLocEnforcesQuota() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockPlayer.hasPermission("testplugin.admin")).thenReturn(false);
            when(mockStore.canSaveLocation(playerUuid, "home", false)).thenReturn(false);
            when(mockStore.getLocationCount(playerUuid)).thenReturn(50);
            when(mockStore.getMaxLocationsPerPlayer()).thenReturn(50);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getQuotaExceeded(50, 50));
            verify(mockStore, never()).storePoint(any(), any(), any(), anyDouble(), anyDouble(), anyDouble());
        }

        @Test
        @DisplayName("Quota: Allows admin to bypass quota")
        void testStoreLocAdminBypassesQuota() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockPlayer.hasPermission("testplugin.admin")).thenReturn(true);
            when(mockPlayer.getServer()).thenReturn(mockServer);
            when(mockServer.getPluginManager()).thenReturn(mock(org.bukkit.plugin.PluginManager.class));
            when(mockServer.getPluginManager().getPlugin("TestPlugin")).thenReturn(mock(org.bukkit.plugin.Plugin.class));
            when(mockServer.getPluginManager().getPlugin("TestPlugin").getConfig()).thenReturn(mock(org.bukkit.configuration.file.FileConfiguration.class));
            when(mockServer.getPluginManager().getPlugin("TestPlugin").getConfig().getBoolean("permissions.admin-bypass-quota", true)).thenReturn(true);
            when(mockStore.canSaveLocation(playerUuid, "home", true)).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "storeloc", 
                new String[]{"home", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockStore).storePoint(playerUuid, "home", "world", 0.0, 64.0, 0.0);
        }
    }

    // ==================== SetLocCommand Tests ====================

    @Nested
    @DisplayName("SetLocCommand Tests")
    class SetLocCommandTests {

        private SetLocCommand command;

        @BeforeEach
        void setUp() {
            command = new SetLocCommand(mockStore, mockMessages);
        }

        @Test
        @DisplayName("Permission: Requires testplugin.basic permission")
        void testSetLocRequiresBasicPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "setloc", 
                new String[]{"home", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoBasicPermission());
        }

        @Test
        @DisplayName("Input Validation: Rejects NaN coordinates")
        void testSetLocRejectsNaN() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.getPoint(playerUuid, "home")).thenReturn(new SavedLocation("world", 0, 64, 0));

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "setloc", 
                new String[]{"home", "NaN", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getInvalidCoordinates());
        }

        @Test
        @DisplayName("Input Validation: Requires existing location")
        void testSetLocRequiresExistingLocation() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.getPoint(playerUuid, "nonexistent")).thenReturn(null);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "setloc", 
                new String[]{"nonexistent", "0", "64", "0"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getLocationNotFound("nonexistent"));
        }
    }

    // ==================== GotoCommand Tests ====================

    @Nested
    @DisplayName("GotoCommand Tests")
    class GotoCommandTests {

        private GotoCommand command;

        @BeforeEach
        void setUp() {
            command = new GotoCommand(mockStore, mockMessages, mockVisibilityCache);
        }

        @Test
        @DisplayName("Permission: Personal locations require testplugin.basic")
        void testGotoPersonalRequiresBasicPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", new String[]{"home"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoBasicPermission());
        }

        @Test
        @DisplayName("Permission: Cross-player access requires testplugin.others")
        void testGotoCrossPlayerRequiresOthersPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.others")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", new String[]{"Player2:home"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoOthersPermission());
        }

        @Test
        @DisplayName("Target Resolution: Resolves personal location syntax")
        void testGotoResolvesPersonalLocation() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            SavedLocation location = new SavedLocation("world", 100, 64, 200);
            when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mockWorld);
            when(mockPlayer.teleport(any(Location.class))).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", new String[]{"home"});

            // Then
            assertTrue(result);
            verify(mockStore).getPoint(playerUuid, "home");
            verify(mockPlayer).teleport(any(Location.class));
        }

        @Test
        @DisplayName("Target Resolution: Resolves cross-player location syntax (player:name)")
        void testGotoResolvesCrossPlayerLocation() {
            // Given
            UUID otherPlayerUuid = UUID.randomUUID();
            when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
            when(mockStore.resolvePlayerUUID("OtherPlayer")).thenReturn(otherPlayerUuid);
            SavedLocation location = new SavedLocation("world", 100, 64, 200);
            when(mockStore.getPoint(otherPlayerUuid, "base")).thenReturn(location);
            bukkit.when(() -> Bukkit.getWorld("world")).thenReturn(mockWorld);
            when(mockPlayer.teleport(any(Location.class))).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", 
                new String[]{"OtherPlayer:base"});

            // Then
            assertTrue(result);
            verify(mockStore).resolvePlayerUUID("OtherPlayer");
            verify(mockStore).getPoint(otherPlayerUuid, "base");
            verify(mockPlayer).sendMessage(mockMessages.getCrossPlayerTeleportSuccess("OtherPlayer", "base"));
        }

        @Test
        @DisplayName("Target Resolution: Returns error for unknown player")
        void testGotoReturnsErrorForUnknownPlayer() {
            // Given
            when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
            when(mockStore.resolvePlayerUUID("UnknownPlayer")).thenReturn(null);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", 
                new String[]{"UnknownPlayer:home"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getPlayerNotFound("UnknownPlayer"));
            verify(mockPlayer, never()).teleport(any(Location.class));
        }

        @Test
        @DisplayName("Input Validation: Enforces name length limits")
        void testGotoEnforcesNameLength() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            String longName = "a".repeat(33);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "goto", new String[]{longName});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNameTooLong(32));
        }
    }

    // ==================== DeleteLocCommand Tests ====================

    @Nested
    @DisplayName("DeleteLocCommand Tests")
    class DeleteLocCommandTests {

        private DeleteLocCommand command;

        @BeforeEach
        void setUp() {
            command = new DeleteLocCommand(mockStore, mockMessages);
        }

        @Test
        @DisplayName("Permission: Requires testplugin.basic permission")
        void testDeleteLocRequiresBasicPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "deleteloc", new String[]{"home"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoBasicPermission());
            verify(mockStore, never()).deletePoint(any(), any());
        }

        @Test
        @DisplayName("Deletion: Successfully deletes existing location")
        void testDeleteLocDeletesLocation() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.getPoint(playerUuid, "home")).thenReturn(new SavedLocation("world", 0, 64, 0));
            when(mockStore.deletePoint(playerUuid, "home")).thenReturn(true);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "deleteloc", new String[]{"home"});

            // Then
            assertTrue(result);
            verify(mockStore).getPoint(playerUuid, "home");
            verify(mockStore).deletePoint(playerUuid, "home");
            verify(mockPlayer).sendMessage(mockMessages.getLocationDeleted("home"));
        }

        @Test
        @DisplayName("Deletion: Returns error for non-existent location")
        void testDeleteLocHandlesNonExistent() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            when(mockStore.getPoint(playerUuid, "nonexistent")).thenReturn(null);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "deleteloc", 
                new String[]{"nonexistent"});

            // Then
            assertTrue(result);
            verify(mockStore, never()).deletePoint(any(), anyString());
            verify(mockPlayer).sendMessage(mockMessages.getLocationNotFound("nonexistent"));
        }
    }

    // ==================== ListLocsCommand Tests ====================

    @Nested
    @DisplayName("ListLocsCommand Tests")
    class ListLocsCommandTests {

        private ListLocsCommand command;

        @BeforeEach
        void setUp() {
            command = new ListLocsCommand(mockStore, mockMessages);
        }

        @Test
        @DisplayName("Permission: Listing own locations requires testplugin.basic")
        void testListLocsOwnRequiresBasicPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "listlocs", new String[]{});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoBasicPermission());
        }

        @Test
        @DisplayName("Permission: Listing other player's locations requires testplugin.others")
        void testListLocsOthersRequiresOthersPermission() {
            // Given
            when(mockPlayer.hasPermission("testplugin.others")).thenReturn(false);

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "listlocs", 
                new String[]{"OtherPlayer"});

            // Then
            assertTrue(result);
            verify(mockPlayer).sendMessage(mockMessages.getNoOthersPermission());
        }

        @Test
        @DisplayName("Target Resolution: Lists own locations when no argument provided")
        void testListLocsListsOwnLocations() {
            // Given
            when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
            Set<String> locations = Set.of("home", "base", "farm");
            when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
            when(mockStore.getPoint(eq(playerUuid), anyString()))
                .thenReturn(new SavedLocation("world", 0, 64, 0));
            when(mockMessages.getOwnListHeader()).thenReturn(Component.text("Your locations:"));
            when(mockMessages.getListEntry(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Component.text("Entry"));

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "listlocs", new String[]{});

            // Then
            assertTrue(result);
            verify(mockStore).getSavedNames(playerUuid);
        }

        @Test
        @DisplayName("Target Resolution: Lists other player's locations when player name provided")
        void testListLocsListsOtherPlayerLocations() {
            // Given
            UUID otherUuid = UUID.randomUUID();
            when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
            when(mockStore.resolvePlayerUUID("OtherPlayer")).thenReturn(otherUuid);
            Set<String> locations = Set.of("spawn");
            when(mockStore.getSavedNames(otherUuid)).thenReturn(locations);
            when(mockStore.getPoint(eq(otherUuid), anyString()))
                .thenReturn(new SavedLocation("world", 100, 64, 100));
            when(mockMessages.getOtherListHeader("OtherPlayer")).thenReturn(Component.text("Other's locations:"));
            when(mockMessages.getListEntry(anyString(), anyString(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Component.text("Entry"));

            // When
            boolean result = command.onCommand(mockPlayer, mockCommand, "listlocs", 
                new String[]{"OtherPlayer"});

            // Then
            assertTrue(result);
            verify(mockStore).resolvePlayerUUID("OtherPlayer");
            verify(mockStore).getSavedNames(otherUuid);
        }
    }
}
