package com.ideflux.testPlugin.listeners;

import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.CoordinateStore.SavedLocation;
import com.ideflux.testPlugin.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for CommandInterceptor verifying:
 * - Translation of #name and #player:name arguments into physical coordinates
 * - Strict enforcement of the whitelisted command set
 * - Prevention of cross-world coordinate injection
 */
class CommandInterceptorTest {

    @Mock
    private CoordinateStore mockStore;

    @Mock
    private MessageManager mockMessages;

    @Mock
    private Player mockPlayer;

    @Mock
    private World mockWorld;

    private CommandInterceptor interceptor;
    private UUID playerUuid;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        
        playerUuid = UUID.randomUUID();
        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getWorld()).thenReturn(mockWorld);
        when(mockWorld.getName()).thenReturn("world");
        when(mockMessages.getParsedCommand(anyString())).thenReturn(Component.text("Parsed"));
        when(mockMessages.getLocationInDifferentWorld(anyString(), anyString(), anyString()))
            .thenReturn(Component.text("Different world"));

        interceptor = new CommandInterceptor(mockStore, mockMessages);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private PlayerCommandPreprocessEvent createEvent(String command) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(mockPlayer, command);
        return event;
    }

    // ==================== Whitelisted Commands Tests ====================

    @Test
    @DisplayName("Whitelist: Processes whitelisted /tp command")
    void testProcessesWhitelistedTpCommand() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #home");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        assertTrue(event.getMessage().contains("100.00"));
        assertTrue(event.getMessage().contains("64.00"));
        assertTrue(event.getMessage().contains("200.00"));
    }

    @Test
    @DisplayName("Whitelist: Processes whitelisted /execute command")
    void testProcessesWhitelistedExecuteCommand() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 50.0, 32.0, 75.0);
        when(mockStore.getPoint(playerUuid, "spawn")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/execute positioned #spawn run say hello");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        assertTrue(event.getMessage().contains("50.00"));
        assertTrue(event.getMessage().contains("32.00"));
        assertTrue(event.getMessage().contains("75.00"));
    }

    @Test
    @DisplayName("Whitelist: Ignores non-whitelisted commands")
    void testIgnoresNonWhitelistedCommands() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        String originalCommand = "/say #home";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should remain unchanged
        assertEquals(originalCommand, event.getMessage());
        verify(mockStore, never()).getPoint(any(), anyString());
    }

    @Test
    @DisplayName("Whitelist: Processes whitelisted Minecraft namespace commands")
    void testProcessesMinecraftNamespaceCommands() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/minecraft:tp @p #home");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        assertTrue(event.getMessage().contains("100.00"));
    }

    // ==================== Coordinate Translation Tests ====================

    @Test
    @DisplayName("Translation: Converts #name to coordinates for personal location")
    void testTranslatesPersonalLocationReference() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 123.45, 67.89, 234.56);
        when(mockStore.getPoint(playerUuid, "base")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #base");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(event, atLeastOnce()).setMessage(messageCaptor.capture());
        String finalMessage = event.getMessage();
        
        assertTrue(finalMessage.contains("123.45"));
        assertTrue(finalMessage.contains("67.89"));
        assertTrue(finalMessage.contains("234.56"));
        assertFalse(finalMessage.contains("#base"));
    }

    @Test
    @DisplayName("Translation: Converts #player:name to coordinates for cross-player location")
    void testTranslatesCrossPlayerLocationReference() {
        // Given
        UUID otherPlayerUuid = UUID.randomUUID();
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        when(mockStore.resolvePlayerUUID("OtherPlayer")).thenReturn(otherPlayerUuid);
        SavedLocation location = new SavedLocation("world", 999.0, 128.0, 888.0);
        when(mockStore.getPoint(otherPlayerUuid, "fortress")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #OtherPlayer:fortress");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        String finalMessage = event.getMessage();
        assertTrue(finalMessage.contains("999.00"));
        assertTrue(finalMessage.contains("128.00"));
        assertTrue(finalMessage.contains("888.00"));
        assertFalse(finalMessage.contains("#OtherPlayer:fortress"));
    }

    @Test
    @DisplayName("Translation: Handles multiple location references in one command")
    void testTranslatesMultipleReferences() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation home = new SavedLocation("world", 0.0, 64.0, 0.0);
        SavedLocation base = new SavedLocation("world", 100.0, 64.0, 100.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(home);
        when(mockStore.getPoint(playerUuid, "base")).thenReturn(base);
        
        PlayerCommandPreprocessEvent event = createEvent("/fill #home #base minecraft:stone");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        String finalMessage = event.getMessage();
        assertTrue(finalMessage.contains("0.00 64.00 0.00"));
        assertTrue(finalMessage.contains("100.00 64.00 100.00"));
    }

    @Test
    @DisplayName("Translation: Uses Locale.US for decimal separator")
    void testUsesUSLocaleForDecimals() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.5, 64.25, 200.75);
        when(mockStore.getPoint(playerUuid, "test")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #test");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        String finalMessage = event.getMessage();
        assertTrue(finalMessage.contains("100.50"));
        assertTrue(finalMessage.contains("64.25"));
        assertTrue(finalMessage.contains("200.75"));
        // Verify it uses period, not comma
        assertFalse(finalMessage.contains("100,50"));
    }

    // ==================== Permission Enforcement Tests ====================

    @Test
    @DisplayName("Permission: Requires testplugin.basic for personal locations")
    void testRequiresBasicPermissionForPersonalLocations() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        String originalCommand = "/tp @p #home";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should not be modified
        assertEquals(originalCommand, event.getMessage());
        verify(mockStore, never()).getPoint(any(), anyString());
    }

    @Test
    @DisplayName("Permission: Requires testplugin.others for cross-player locations")
    void testRequiresOthersPermissionForCrossPlayerLocations() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(false);
        
        String originalCommand = "/tp @p #OtherPlayer:home";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should not be modified
        assertEquals(originalCommand, event.getMessage());
        verify(mockStore, never()).resolvePlayerUUID(anyString());
    }

    // ==================== Cross-World Injection Prevention Tests ====================

    @Test
    @DisplayName("Cross-World: Prevents coordinate injection from different world")
    void testPreventsCrossWorldCoordinateInjection() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation netherLocation = new SavedLocation("world_nether", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "nether_base")).thenReturn(netherLocation);
        when(mockWorld.getName()).thenReturn("world"); // Player is in overworld
        
        String originalCommand = "/tp @p #nether_base";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should not be modified and warning should be sent
        assertEquals(originalCommand, event.getMessage());
        verify(mockPlayer).sendMessage(mockMessages.getLocationInDifferentWorld(
            "nether_base", "world_nether", "world"));
    }

    @Test
    @DisplayName("Cross-World: Allows coordinates from same world")
    void testAllowsCoordinatesFromSameWorld() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        when(mockWorld.getName()).thenReturn("world");
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #home");

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should be modified
        assertTrue(event.getMessage().contains("100.00"));
        verify(mockPlayer, never()).sendMessage(any(Component.class));
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Edge Case: Skips non-existent location references")
    void testSkipsNonExistentLocationReferences() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        when(mockStore.getPoint(playerUuid, "nonexistent")).thenReturn(null);
        
        String originalCommand = "/tp @p #nonexistent";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should remain unchanged
        assertEquals(originalCommand, event.getMessage());
    }

    @Test
    @DisplayName("Edge Case: Handles invalid cross-player syntax")
    void testHandlesInvalidCrossPlayerSyntax() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        
        String originalCommand = "/tp @p #Player:";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should remain unchanged
        assertEquals(originalCommand, event.getMessage());
    }

    @Test
    @DisplayName("Edge Case: Skips reference when player not found")
    void testSkipsReferenceWhenPlayerNotFound() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        when(mockStore.resolvePlayerUUID("UnknownPlayer")).thenReturn(null);
        
        String originalCommand = "/tp @p #UnknownPlayer:home";
        PlayerCommandPreprocessEvent event = createEvent(originalCommand);

        // When
        interceptor.onCommandPreprocess(event);

        // Then - command should remain unchanged
        assertEquals(originalCommand, event.getMessage());
    }

    @Test
    @DisplayName("Edge Case: Notifies player when command is modified")
    void testNotifiesPlayerWhenCommandModified() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #home");

        // When
        interceptor.onCommandPreprocess(event);

        // Then - player should be notified
        verify(mockPlayer).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("Edge Case: Case insensitive location name matching")
    void testCaseInsensitiveLocationMatching() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        SavedLocation location = new SavedLocation("world", 100.0, 64.0, 200.0);
        when(mockStore.getPoint(playerUuid, "home")).thenReturn(location);
        
        PlayerCommandPreprocessEvent event = createEvent("/tp @p #HOME");

        // When
        interceptor.onCommandPreprocess(event);

        // Then
        verify(mockStore).getPoint(playerUuid, "home");
        assertTrue(event.getMessage().contains("100.00"));
    }
}
