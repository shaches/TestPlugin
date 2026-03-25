package com.ideflux.testPlugin;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for VisibilityCache to verify accurate tracking of player visibility states
 * and safe access across threads during player join and quit events.
 */
class VisibilityCacheTest {

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    @Mock
    private Server mockServer;

    @Mock
    private BukkitScheduler mockScheduler;

    @Mock
    private Player player1;

    @Mock
    private Player player2;

    @Mock
    private Player player3;

    private VisibilityCache visibilityCache;
    private MockedStatic<Bukkit> bukkit;
    private AutoCloseable mocks;
    private UUID uuid1;
    private UUID uuid2;
    private UUID uuid3;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);

        when(mockPlugin.getLogger()).thenReturn(mockLogger);

        // Setup UUIDs
        uuid1 = UUID.randomUUID();
        uuid2 = UUID.randomUUID();
        uuid3 = UUID.randomUUID();

        when(player1.getUniqueId()).thenReturn(uuid1);
        when(player1.getName()).thenReturn("Player1");
        when(player2.getUniqueId()).thenReturn(uuid2);
        when(player2.getName()).thenReturn("Player2");
        when(player3.getUniqueId()).thenReturn(uuid3);
        when(player3.getName()).thenReturn("Player3");

        // Mock Bukkit static methods BEFORE creating VisibilityCache
        bukkit = mockStatic(Bukkit.class);

        // Mock isPrimaryThread to return true for tests
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

        // Mock getScheduler to return mockScheduler
        bukkit.when(Bukkit::getScheduler).thenReturn(mockScheduler);

        // Mock scheduler to run tasks immediately for testing
        when(mockScheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(invocation -> {
                Runnable task = invocation.getArgument(1);
                task.run(); // Run once immediately
                return mock(BukkitTask.class);
            });

        // Initialize visibility cache
        visibilityCache = new VisibilityCache(mockPlugin);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (bukkit != null) {
            bukkit.close();
        }
        mocks.close();
    }

    @Test
    void testCanSee_ReturnsTrueWhenVisible() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        boolean canSee = visibilityCache.canSee(uuid1, uuid2);

        // Then
        assertTrue(canSee);
    }

    @Test
    void testCanSee_ReturnsFalseWhenNotVisible() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(false);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        boolean canSee = visibilityCache.canSee(uuid1, uuid2);

        // Then
        assertFalse(canSee);
    }

    @Test
    void testCanSee_ReturnsFalseForSelf() {
        // Given
        Collection<Player> players = Collections.singletonList(player1);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When - player checking visibility of themselves
        boolean canSee = visibilityCache.canSee(uuid1, uuid1);

        // Then
        assertFalse(canSee);
    }

    @Test
    void testCanSee_ReturnsFalseWhenObserverNotOnline() {
        // Given
        Collection<Player> players = Collections.singletonList(player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player2);

        // When - player1 not online
        boolean canSee = visibilityCache.canSee(uuid1, uuid2);

        // Then
        assertFalse(canSee);
    }

    @Test
    void testGetVisiblePlayers_ReturnsAllVisiblePlayers() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2, player3);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        when(player1.canSee(player3)).thenReturn(true);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        Set<UUID> visible = visibilityCache.getVisiblePlayers(uuid1);

        // Then
        assertEquals(2, visible.size());
        assertTrue(visible.contains(uuid2));
        assertTrue(visible.contains(uuid3));
        assertFalse(visible.contains(uuid1)); // Should not include self
    }

    @Test
    void testGetVisiblePlayers_ExcludesVanishedPlayers() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2, player3);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        when(player1.canSee(player3)).thenReturn(false); // player3 is vanished
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        Set<UUID> visible = visibilityCache.getVisiblePlayers(uuid1);

        // Then
        assertEquals(1, visible.size());
        assertTrue(visible.contains(uuid2));
        assertFalse(visible.contains(uuid3));
    }

    @Test
    void testGetVisiblePlayers_ReturnsEmptyForOfflinePlayer() {
        // Given
        Collection<Player> players = Collections.singletonList(player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player2);

        // When - uuid1 not online
        Set<UUID> visible = visibilityCache.getVisiblePlayers(uuid1);

        // Then
        assertTrue(visible.isEmpty());
    }

    @Test
    void testGetVisiblePlayers_ReturnsImmutableSet() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        Set<UUID> visible = visibilityCache.getVisiblePlayers(uuid1);

        // Then - attempting to modify should throw exception
        assertThrows(UnsupportedOperationException.class, () -> {
            visible.add(UUID.randomUUID());
        });
    }

    @Test
    void testIsOnline_ReturnsTrueForOnlinePlayer() {
        // Given
        Collection<Player> players = Collections.singletonList(player1);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        boolean online = visibilityCache.isOnline(uuid1);

        // Then
        assertTrue(online);
    }

    @Test
    void testIsOnline_ReturnsFalseForOfflinePlayer() {
        // Given
        Collection<Player> players = Collections.singletonList(player1);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When - uuid2 not online
        boolean online = visibilityCache.isOnline(uuid2);

        // Then
        assertFalse(online);
    }

    @Test
    void testGetOnlinePlayers_ReturnsAllOnlinePlayers() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2, player3);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        Set<UUID> online = visibilityCache.getOnlinePlayers();

        // Then
        assertEquals(3, online.size());
        assertTrue(online.contains(uuid1));
        assertTrue(online.contains(uuid2));
        assertTrue(online.contains(uuid3));
    }

    @Test
    void testGetOnlinePlayers_ReturnsImmutableSet() {
        // Given
        Collection<Player> players = Collections.singletonList(player1);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        
        // Trigger cache update
        visibilityCache.onPlayerJoin(player1);

        // When
        Set<UUID> online = visibilityCache.getOnlinePlayers();

        // Then - attempting to modify should throw exception
        assertThrows(UnsupportedOperationException.class, () -> {
            online.add(UUID.randomUUID());
        });
    }

    @Test
    void testOnPlayerJoin_UpdatesCache() {
        // Given
        Collection<Player> players = Collections.singletonList(player1);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);

        // When
        visibilityCache.onPlayerJoin(player1);

        // Then
        assertTrue(visibilityCache.isOnline(uuid1));
    }

    @Test
    void testOnPlayerQuit_RemovesPlayerFromCache() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        visibilityCache.onPlayerJoin(player1);
        
        // Verify player1 is online
        assertTrue(visibilityCache.isOnline(uuid1));

        // When
        visibilityCache.onPlayerQuit(player1);

        // Then
        assertFalse(visibilityCache.isOnline(uuid1));
    }

    @Test
    void testOnPlayerQuit_RemovesFromOtherPlayersVisibility() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player2.canSee(player1)).thenReturn(true);
        
        visibilityCache.onPlayerJoin(player1);
        
        // Verify player2 can see player1
        assertTrue(visibilityCache.canSee(uuid2, uuid1));

        // When - player1 quits
        visibilityCache.onPlayerQuit(player1);

        // Then - player1 should be removed from player2's visibility
        assertFalse(visibilityCache.canSee(uuid2, uuid1));
    }

    @Test
    void testThreadSafety_ConcurrentReads() throws InterruptedException {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        visibilityCache.onPlayerJoin(player1);

        // When - multiple threads reading concurrently
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        List<Boolean> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    boolean canSee = visibilityCache.canSee(uuid1, uuid2);
                    results.add(canSee);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - all reads should succeed
        assertEquals(threadCount * 1000, results.size());
        assertTrue(results.stream().allMatch(result -> result));
    }

    @Test
    void testAsyncSafety_CanBeCalledFromNonMainThread() {
        // Given
        Collection<Player> players = Arrays.asList(player1, player2);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(players);
        when(player1.canSee(player2)).thenReturn(true);
        visibilityCache.onPlayerJoin(player1);

        // When - simulate async context (non-main thread)
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
        
        // Then - should still work without calling Bukkit.getOnlinePlayers()
        assertDoesNotThrow(() -> {
            boolean canSee = visibilityCache.canSee(uuid1, uuid2);
            Set<UUID> visible = visibilityCache.getVisiblePlayers(uuid1);
            boolean online = visibilityCache.isOnline(uuid1);
        });
    }
}
