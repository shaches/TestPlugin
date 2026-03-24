package com.ideflux.testPlugin.listeners;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.ideflux.testPlugin.CoordinateStore;
import com.ideflux.testPlugin.VisibilityCache;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for TabCompletionInterceptor verifying:
 * - Accurate asynchronous auto-completion suggestions for both personal locations
 *   and visible online players
 * - Thread-safe access using VisibilityCache to avoid synchronous API calls from async context
 */
class TabCompletionInterceptorTest {

    @Mock
    private CoordinateStore mockStore;

    @Mock
    private VisibilityCache mockVisibilityCache;

    @Mock
    private Player mockPlayer;

    @Mock
    private Player otherPlayer1;

    @Mock
    private Player otherPlayer2;

    private TabCompletionInterceptor interceptor;
    private MockedStatic<Bukkit> bukkit;
    private UUID playerUuid;
    private UUID otherUuid1;
    private UUID otherUuid2;
    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        playerUuid = UUID.randomUUID();
        otherUuid1 = UUID.randomUUID();
        otherUuid2 = UUID.randomUUID();

        when(mockPlayer.getUniqueId()).thenReturn(playerUuid);
        when(mockPlayer.getName()).thenReturn("TestPlayer");
        when(otherPlayer1.getUniqueId()).thenReturn(otherUuid1);
        when(otherPlayer1.getName()).thenReturn("Player1");
        when(otherPlayer2.getUniqueId()).thenReturn(otherUuid2);
        when(otherPlayer2.getName()).thenReturn("Player2");

        interceptor = new TabCompletionInterceptor(mockStore, mockVisibilityCache);

        // Mock Bukkit
        bukkit = mockStatic(Bukkit.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bukkit != null) {
            bukkit.close();
        }
        mocks.close();
    }

    private AsyncTabCompleteEvent createEvent(String buffer, List<String> initialCompletions) {
        AsyncTabCompleteEvent event = mock(AsyncTabCompleteEvent.class);
        when(event.getSender()).thenReturn(mockPlayer);
        when(event.getBuffer()).thenReturn(buffer);
        when(event.getCompletions()).thenReturn(initialCompletions);
        return event;
    }

    // ==================== Personal Location Completion Tests ====================

    @Test
    @DisplayName("Personal Locations: Suggests personal locations starting with #")
    void testSuggestsPersonalLocations() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home", "base", "farm");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#home") && 
            completions.contains("#base") && 
            completions.contains("#farm")
        ));
    }

    @Test
    @DisplayName("Personal Locations: Filters suggestions based on partial input")
    void testFiltersPersonalLocationsByPartialInput() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home", "house", "base", "farm");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #ho", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#home") && 
            completions.contains("#house") && 
            !completions.contains("#base") &&
            !completions.contains("#farm")
        ));
    }

    @Test
    @DisplayName("Personal Locations: Case insensitive filtering")
    void testCaseInsensitiveFiltering() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home", "base");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #HO", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#home")
        ));
    }

    @Test
    @DisplayName("Personal Locations: Requires testplugin.basic permission")
    void testPersonalLocationsRequireBasicPermission() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(false);
        Set<String> locations = Set.of("home");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            !completions.contains("#home")
        ));
    }

    // ==================== Cross-Player Location Completion Tests ====================

    @Test
    @DisplayName("Cross-Player: Suggests player names with colon suffix")
    void testSuggestsPlayerNamesWithColon() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1, otherUuid2);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        when(mockStore.getSavedNames(otherUuid2)).thenReturn(Set.of("base"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        bukkit.when(() -> Bukkit.getPlayer(otherUuid2)).thenReturn(otherPlayer2);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:") && 
            completions.contains("#Player2:")
        ));
    }

    @Test
    @DisplayName("Cross-Player: Filters player names by partial input")
    void testFiltersPlayerNamesByPartialInput() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1, otherUuid2);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        when(mockStore.getSavedNames(otherUuid2)).thenReturn(Set.of("base"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        bukkit.when(() -> Bukkit.getPlayer(otherUuid2)).thenReturn(otherPlayer2);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #play", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:") && 
            completions.contains("#Player2:")
        ));
    }

    @Test
    @DisplayName("Cross-Player: Suggests location names after player:prefix")
    void testSuggestsLocationNamesAfterPlayerPrefix() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        when(mockStore.resolvePlayerUUID("Player1")).thenReturn(otherUuid1);
        Set<String> locations = Set.of("home", "base", "farm");
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #Player1:", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:home") && 
            completions.contains("#Player1:base") && 
            completions.contains("#Player1:farm")
        ));
    }

    @Test
    @DisplayName("Cross-Player: Filters location names by partial input after colon")
    void testFiltersLocationNamesAfterColon() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        when(mockStore.resolvePlayerUUID("Player1")).thenReturn(otherUuid1);
        Set<String> locations = Set.of("home", "house", "base");
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #Player1:ho", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:home") && 
            completions.contains("#Player1:house") && 
            !completions.contains("#Player1:base")
        ));
    }

    @Test
    @DisplayName("Cross-Player: Requires testplugin.others permission")
    void testCrossPlayerRequiresOthersPermission() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(false);
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - should not suggest player names
        verify(event).setCompletions(argThat(completions -> 
            !completions.contains("#Player1:")
        ));
    }

    @Test
    @DisplayName("Cross-Player: Only suggests players with saved locations")
    void testOnlySuggestsPlayersWithLocations() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1, otherUuid2);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        when(mockStore.getSavedNames(otherUuid2)).thenReturn(Collections.emptySet()); // No locations
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        bukkit.when(() -> Bukkit.getPlayer(otherUuid2)).thenReturn(otherPlayer2);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:") && 
            !completions.contains("#Player2:")
        ));
    }

    // ==================== Visibility and Thread Safety Tests ====================

    @Test
    @DisplayName("Visibility: Uses VisibilityCache for async-safe player checks")
    void testUsesVisibilityCacheForAsyncSafety() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - should use VisibilityCache, not Bukkit.getOnlinePlayers()
        verify(mockVisibilityCache).getVisiblePlayers(playerUuid);
        bukkit.verify(() -> Bukkit.getOnlinePlayers(), never());
    }

    @Test
    @DisplayName("Visibility: Excludes vanished players from suggestions")
    void testExcludesVanishedPlayers() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        // otherUuid2 is NOT in visible players (vanished)
        Set<UUID> visiblePlayers = Set.of(otherUuid1);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - should only suggest visible player
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#Player1:") && 
            !completions.contains("#Player2:")
        ));
    }

    @Test
    @DisplayName("Visibility: Handles null player from Bukkit.getPlayer gracefully")
    void testHandlesNullPlayerGracefully() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<UUID> visiblePlayers = Set.of(otherUuid1);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("home"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(null); // Player went offline
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> interceptor.onAsyncTabComplete(event));
    }

    // ==================== Edge Cases Tests ====================

    @Test
    @DisplayName("Edge Case: Ignores non-# prefixed arguments")
    void testIgnoresNonHashPrefixedArguments() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        List<String> originalCompletions = new ArrayList<>(List.of("player1", "player2"));
        AsyncTabCompleteEvent event = createEvent("/tp @p ho", originalCompletions);

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - completions should not be modified
        verify(event, never()).setCompletions(any());
    }

    @Test
    @DisplayName("Edge Case: Handles empty location set")
    void testHandlesEmptyLocationSet() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        when(mockStore.getSavedNames(playerUuid)).thenReturn(Collections.emptySet());
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> interceptor.onAsyncTabComplete(event));
    }

    @Test
    @DisplayName("Edge Case: Handles player UUID not resolving")
    void testHandlesPlayerUuidNotResolving() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        when(mockStore.resolvePlayerUUID("UnknownPlayer")).thenReturn(null);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #UnknownPlayer:", new ArrayList<>());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> interceptor.onAsyncTabComplete(event));
    }

    @Test
    @DisplayName("Edge Case: Preserves existing completions")
    void testPreservesExistingCompletions() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        List<String> existingCompletions = new ArrayList<>(List.of("existing1", "existing2"));
        AsyncTabCompleteEvent event = createEvent("/tp @p #", existingCompletions);

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - should preserve existing completions and add new ones
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("existing1") && 
            completions.contains("existing2") &&
            completions.contains("#home")
        ));
    }

    @Test
    @DisplayName("Edge Case: Handles invalid colon syntax")
    void testHandlesInvalidColonSyntax() {
        // Given
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #:", new ArrayList<>());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> interceptor.onAsyncTabComplete(event));
    }

    @Test
    @DisplayName("Threading: Can be called from async context")
    void testCanBeCalledFromAsyncContext() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        Set<String> locations = Set.of("home");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(locations);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When - simulate async execution
        Thread asyncThread = new Thread(() -> {
            interceptor.onAsyncTabComplete(event);
        });

        // Then - should complete without exception
        assertDoesNotThrow(() -> {
            asyncThread.start();
            asyncThread.join(1000);
        });
    }

    @Test
    @DisplayName("Combined: Suggests both personal locations and player names")
    void testSuggestsBothPersonalAndCrossPlayer() {
        // Given
        when(mockPlayer.hasPermission("testplugin.basic")).thenReturn(true);
        when(mockPlayer.hasPermission("testplugin.others")).thenReturn(true);
        Set<String> personalLocations = Set.of("home");
        when(mockStore.getSavedNames(playerUuid)).thenReturn(personalLocations);
        Set<UUID> visiblePlayers = Set.of(otherUuid1);
        when(mockVisibilityCache.getVisiblePlayers(playerUuid)).thenReturn(visiblePlayers);
        when(mockStore.getSavedNames(otherUuid1)).thenReturn(Set.of("base"));
        bukkit.when(() -> Bukkit.getPlayer(otherUuid1)).thenReturn(otherPlayer1);
        
        AsyncTabCompleteEvent event = createEvent("/tp @p #", new ArrayList<>());

        // When
        interceptor.onAsyncTabComplete(event);

        // Then - should suggest both
        verify(event).setCompletions(argThat(completions -> 
            completions.contains("#home") && 
            completions.contains("#Player1:")
        ));
    }
}
