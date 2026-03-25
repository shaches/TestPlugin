package com.ideflux.testPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MessageManager to ensure correct parsing of both modern MiniMessage tags
 * and legacy ampersand color codes, along with accurate placeholder replacements.
 */
class MessageManagerTest {

    @TempDir
    Path tempDir;

    @Mock
    private JavaPlugin mockPlugin;

    @Mock
    private Logger mockLogger;

    private MessageManager messageManager;
    private File messagesFile;
    private AutoCloseable mocks;
    private PlainTextComponentSerializer plainSerializer;

    @BeforeEach
    void setUp() throws IOException {
        mocks = MockitoAnnotations.openMocks(this);

        File dataFolder = tempDir.toFile();
        when(mockPlugin.getDataFolder()).thenReturn(dataFolder);
        when(mockPlugin.getLogger()).thenReturn(mockLogger);

        // Create the messages.yml file
        messagesFile = new File(dataFolder, "messages.yml");
        dataFolder.mkdirs();

        // Mock getResource to return null (tests will create the file)
        when(mockPlugin.getResource("messages.yml")).thenReturn(null);

        plainSerializer = PlainTextComponentSerializer.plainText();
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    private void createMessagesFile(Map<String, Object> messages) throws IOException {
        FileConfiguration config = new YamlConfiguration();
        messages.forEach(config::set);
        config.save(messagesFile);
    }

    @Test
    void testGetMessage_ReturnsMiniMessageFormat() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "<red>This is red text</red>");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("This is red text", plain);
    }

    @Test
    void testGetMessage_ReturnsLegacyAmpersandFormat() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "&aThis is green text");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("This is green text", plain);
    }

    @Test
    void testGetMessage_ReturnsPlainText() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Plain text message");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Plain text message", plain);
    }

    @Test
    void testGetMessage_ReturnsNotFoundForMissingKey() throws IOException {
        // Given
        createMessagesFile(new HashMap<>());
        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("nonexistent.key");
        String plain = plainSerializer.serialize(component);

        // Then
        assertTrue(plain.contains("Message not found"));
        assertTrue(plain.contains("nonexistent.key"));
    }

    @Test
    void testGetMessageWithPlaceholders_ReplacesSinglePlaceholder() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Hello, {name}!");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message", "name", "Player");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Hello, Player!", plain);
    }

    @Test
    void testGetMessageWithPlaceholders_ReplacesMultiplePlaceholders() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Player {player} has {count} items");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", "TestPlayer");
        placeholders.put("count", "42");
        Component component = messageManager.getMessage("test.message", placeholders);
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Player TestPlayer has 42 items", plain);
    }

    @Test
    void testGetMessageWithPlaceholders_HandlesColorCodes() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "<green>{name}</green> teleported to <yellow>{location}</yellow>");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", "Player");
        placeholders.put("location", "spawn");
        Component component = messageManager.getMessage("test.message", placeholders);
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Player teleported to spawn", plain);
    }

    @Test
    void testGetMessageWithPlaceholders_LeavesUnreplacedPlaceholders() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Player {player} at {location}");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When - only replace one placeholder
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("player", "TestPlayer");
        Component component = messageManager.getMessage("test.message", placeholders);
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Player TestPlayer at {location}", plain);
    }

    @Test
    void testGetLocationSaved_FormatsCorrectly() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("storage.saved", "Location <green>{name}</green> saved!");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getLocationSaved("home");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Location home saved!", plain);
    }

    @Test
    void testGetQuotaExceeded_FormatsMultiplePlaceholders() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("storage.quota-exceeded", "Quota exceeded: {current}/{max} locations");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getQuotaExceeded(50, 50);
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Quota exceeded: 50/50 locations", plain);
    }

    @Test
    void testGetListEntry_FormatsAllCoordinates() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("list.entry", "  <yellow>{name}</yellow>: {world} ({x}, {y}, {z})");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getListEntry("home", "world", 100.5, 64.0, 200.75);
        String plain = plainSerializer.serialize(component);

        // Then
        assertTrue(plain.contains("home"));
        assertTrue(plain.contains("world"));
        assertTrue(plain.contains("100.50"));
        assertTrue(plain.contains("64.00"));
        assertTrue(plain.contains("200.75"));
    }

    @Test
    void testGetLocationInDifferentWorld_FormatsAllFields() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("errors.location-different-world", 
            "Location {name} is in {saved_world}, but you are in {current_world}");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getLocationInDifferentWorld("home", "world", "world_nether");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Location home is in world, but you are in world_nether", plain);
    }

    @Test
    void testReload_ReloadsConfiguration() throws IOException, InterruptedException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Original message");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);
        String originalMessage = plainSerializer.serialize(messageManager.getMessage("test.message"));

        // When - modify file and reload
        Thread.sleep(100); // Ensure file modification time changes
        messages.put("test.message", "Updated message");
        createMessagesFile(messages);
        messageManager.reload();

        // Then
        String updatedMessage = plainSerializer.serialize(messageManager.getMessage("test.message"));
        assertEquals("Original message", originalMessage);
        assertEquals("Updated message", updatedMessage);
    }

    @Test
    void testGetConsoleMessage_ReturnsPlainTextWithoutFormatting() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "<red>Colored text</red>");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        String console = messageManager.getConsoleMessage("test.message");

        // Then
        assertEquals("<red>Colored text</red>", console);
    }

    @Test
    void testGetConsoleMessageWithPlaceholders_ReplacesPlaceholders() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Player {name} logged in");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("name", "TestPlayer");
        String console = messageManager.getConsoleMessage("test.message", placeholders);

        // Then
        assertEquals("Player TestPlayer logged in", console);
    }

    @Test
    void testMiniMessage_HandlesHexColors() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "<#FF5555>Red text</#FF5555>");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Red text", plain);
    }

    @Test
    void testMiniMessage_HandlesGradients() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "<gradient:red:blue>Gradient text</gradient>");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Gradient text", plain);
    }

    @Test
    void testLegacyFormat_HandlesMultipleColorCodes() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "&a&lGreen and Bold");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Green and Bold", plain);
    }

    @Test
    void testMixedFormat_FallsBackToLegacy() throws IOException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "&cRed &aGreen");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When
        Component component = messageManager.getMessage("test.message");
        String plain = plainSerializer.serialize(component);

        // Then
        assertEquals("Red Green", plain);
    }

    @Test
    void testThreadSafety_ConcurrentMessageRetrieval() throws IOException, InterruptedException {
        // Given
        Map<String, Object> messages = new HashMap<>();
        messages.put("test.message", "Test message");
        createMessagesFile(messages);

        messageManager = new MessageManager(mockPlugin);

        // When - multiple threads accessing messages
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    Component component = messageManager.getMessage("test.message");
                    assertNotNull(component);
                }
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - should complete without exception
    }
}
