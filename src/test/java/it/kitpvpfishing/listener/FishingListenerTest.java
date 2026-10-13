package it.kitpvpfishing.listener;

import it.kitpvpfishing.config.ConfigManager;
import it.kitpvpfishing.reward.RewardDelivery;
import it.kitpvpfishing.testutil.Fakes;
import it.kitpvpfishing.testutil.Fakes.FakeCaughtItem;
import it.kitpvpfishing.testutil.Fakes.FakePlayer;
import it.kitpvpfishing.testutil.Fakes.FakeWorld;
import it.kitpvpfishing.testutil.MiniEventBus;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import static it.kitpvpfishing.testutil.TestConfigs.COMMAND_REWARDS;
import static it.kitpvpfishing.testutil.TestConfigs.categories;
import static it.kitpvpfishing.testutil.TestConfigs.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FishingListenerTest {

    private static final Set<Material> DIAMOND_ARMOR = EnumSet.of(
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Fakes.FakeServer server;
    private Fakes.CapturingLogger log;
    private ConfigManager configManager;
    private File configFile;
    private MiniEventBus bus;
    private FakeWorld world;
    private FakePlayer player;

    @Before
    public void setUp() throws IOException {
        server = Fakes.server();
        log = new Fakes.CapturingLogger();
        configFile = folder.newFile("config.yml");
        configManager = new ConfigManager(configFile, log.logger);
        bus = new MiniEventBus();
        bus.register(new FishingListener(configManager, new RewardDelivery(server.proxy, log.logger), log.logger, new Random(1234)));
        world = new FakeWorld("kitpvp");
        player = new FakePlayer("Steve", world);
    }

    // ------------------------------------------------------------------ stati senza ricompensa

    @Test
    public void onlySuccessfulCatchesGiveRewards() throws IOException {
        configure(categories(true, true, true) + COMMAND_REWARDS);
        for (PlayerFishEvent.State state : PlayerFishEvent.State.values()) {
            if (state == PlayerFishEvent.State.CAUGHT_FISH) {
                continue;
            }
            FakeCaughtItem caught = new FakeCaughtItem(Fakes.vanillaLoot());
            Entity entity = state == PlayerFishEvent.State.CAUGHT_ENTITY ? new FakePlayer("Alex", world).proxy : null;
            bus.call(new PlayerFishEvent(player.proxy, entity, null, state));
            assertUntouched(caught);
        }
        assertNoRewardGiven();
    }

    // ------------------------------------------------------------------ categorie

    @Test
    public void fishReplacesOriginalLootWithExactlyOneFish() throws IOException {
        configure(categories(true, false, false));
        FakeCaughtItem caught = catchOnce();
        assertEquals(0, caught.removeCalls);
        assertEquals(1, caught.setStackCalls);
        assertEquals(Material.RAW_FISH, caught.stack.getType());
        assertEquals(1, caught.stack.getAmount());
        assertTrue(player.inventory.isEmpty());
        assertTrue(world.dropped.isEmpty());
        assertTrue(server.dispatched.isEmpty());
        assertEquals(1, player.messages.size());
        assertTrue(player.messages.get(0).startsWith(ChatColor.translateAlternateColorCodes('&', "&8[&bPesca&8] &r")));
    }

    @Test
    public void armorGoesToInventoryAndOriginalLootIsRemoved() throws IOException {
        configure(categories(false, true, false));
        FakeCaughtItem caught = catchOnce();
        assertEquals(1, caught.removeCalls);
        assertEquals(0, caught.setStackCalls);
        assertEquals(1, player.inventory.size());
        assertTrue(world.dropped.isEmpty());
        ItemStack armor = player.inventory.get(0);
        assertTrue(DIAMOND_ARMOR.contains(armor.getType()));
        assertEquals(1, armor.getAmount());
        int protection = Fakes.state(armor.getItemMeta()).enchants.get(Enchantment.PROTECTION_ENVIRONMENTAL);
        int unbreaking = Fakes.state(armor.getItemMeta()).enchants.get(Enchantment.DURABILITY);
        assertTrue(protection >= 4 && protection <= 10);
        assertTrue(unbreaking >= 3 && unbreaking <= 10);
        assertTrue(player.messages.get(0).contains("Protezione"));
        assertEquals(1, player.messages.size());
    }

    @Test
    public void fullInventoryDropsTheSinglePieceNearThePlayer() throws IOException {
        configure(categories(false, true, false));
        player.inventoryFull = true;
        FakeCaughtItem caught = catchOnce();
        assertEquals(1, caught.removeCalls);
        assertTrue(player.inventory.isEmpty());
        assertEquals(1, world.dropped.size());
        assertTrue(DIAMOND_ARMOR.contains(world.dropped.get(0).getType()));
        assertEquals(2, player.messages.size());
        assertTrue(player.messages.get(1).contains("Inventario pieno"));
    }

    @Test
    public void commandRewardRunsAllItsCommandsOnceAndRemovesOriginalLoot() throws IOException {
        configure(categories(false, false, true) + COMMAND_REWARDS);
        FakeCaughtItem caught = catchOnce();
        assertEquals(1, caught.removeCalls);
        assertEquals(Arrays.asList("givekey Steve vip 1", "eco give Steve 100"), server.dispatched);
        assertTrue(player.inventory.isEmpty());
        assertTrue(world.dropped.isEmpty());
        assertEquals(1, player.messages.size());
        assertTrue(player.messages.get(0).endsWith(ChatColor.translateAlternateColorCodes('&', "&7Hai vinto &6Key VIP &7(Steve)")));
    }

    @Test
    public void unknownCommandsAreLoggedAndDoNotStopTheOthers() throws IOException {
        configure(categories(false, false, true) + COMMAND_REWARDS);
        server.unknownCommands.add("givekey");
        catchOnce();
        assertEquals(2, server.dispatched.size());
        assertTrue(log.contains(Level.WARNING, "comando non riconosciuto dal server: /givekey Steve vip 1"));
    }

    // ------------------------------------------------------------------ cancellazioni di altri plugin

    @Test
    public void eventsAlreadyCancelledByOtherPluginsAreIgnored() throws IOException {
        configure(categories(true, true, true) + COMMAND_REWARDS);
        bus.register(EventPriority.NORMAL, false, new MiniEventBus.Handler<PlayerFishEvent>() {
            @Override
            public void handle(PlayerFishEvent event) {
                event.setCancelled(true);
            }
        });
        for (int i = 0; i < 50; i++) {
            assertUntouched(catchOnce());
        }
        assertNoRewardGiven();
    }

    @Test
    public void cancellationAfterSelectionDeliversNothing() throws IOException {
        configure(categories(false, true, true) + COMMAND_REWARDS);
        // Registrato dopo il nostro listener: alla stessa priorita' viene eseguito dopo.
        bus.register(EventPriority.HIGHEST, false, new MiniEventBus.Handler<PlayerFishEvent>() {
            @Override
            public void handle(PlayerFishEvent event) {
                event.setCancelled(true);
            }
        });
        for (int i = 0; i < 50; i++) {
            catchOnce();
        }
        assertNoRewardGiven();
    }

    // ------------------------------------------------------------------ filtri globali

    @Test
    public void worldsOutsideTheListKeepVanillaFishing() throws IOException {
        configure(yaml("worlds: ['arena']") + categories(true, true, false));
        assertUntouched(catchOnce());
        assertNoRewardGiven();

        configure(yaml("worlds: ['ARENA', 'KitPvP']") + categories(false, true, false));
        assertEquals(1, catchOnce().removeCalls);
        assertEquals(1, player.inventory.size());
    }

    @Test
    public void disabledPluginKeepsVanillaFishing() throws IOException {
        configure(yaml("enabled: false") + categories(true, true, false));
        assertUntouched(catchOnce());
        assertNoRewardGiven();
    }

    @Test
    public void noValidCategoryKeepsVanillaFishingAndWarnsOnce() throws IOException {
        configure(categories(false, false, true)); // comandi attivi ma senza ricompense
        for (int i = 0; i < 3; i++) {
            assertUntouched(catchOnce());
        }
        assertNoRewardGiven();
        assertEquals(1, countLogs(Level.WARNING, "Nessuna categoria di ricompensa valida nel config"));
    }

    @Test
    public void catchWithoutItemEntityIsLeftVanilla() throws IOException {
        configure(categories(false, true, false));
        for (int i = 0; i < 2; i++) {
            bus.call(new PlayerFishEvent(player.proxy, null, null, PlayerFishEvent.State.CAUGHT_FISH));
        }
        assertNoRewardGiven();
        assertEquals(1, countLogs(Level.WARNING, "Cattura senza oggetto pescato"));
    }

    @Test
    public void invalidReloadKeepsPreviousBehaviour() throws IOException {
        configure(categories(false, true, false));
        write(yaml("categories:", "  armor: {enabled: true, weight: tanto}"));
        assertTrue(configManager.reload().hasErrors());
        assertEquals(1, catchOnce().removeCalls);
        assertEquals(1, player.inventory.size());
    }

    // ------------------------------------------------------------------ messaggi

    @Test
    public void rewardMessagesCanBeDisabled() throws IOException {
        configure(categories(true, false, false) + yaml("messages:", "  reward-messages-enabled: false"));
        catchOnce();
        assertTrue(player.messages.isEmpty());

        configure(categories(false, false, true) + COMMAND_REWARDS + yaml("messages:", "  reward-messages-enabled: false"));
        catchOnce();
        assertTrue(player.messages.isEmpty());
        assertEquals(2, server.dispatched.size());

        // L'avviso di inventario pieno non e' un messaggio di ricompensa e resta attivo.
        configure(categories(false, true, false) + yaml("messages:", "  reward-messages-enabled: false"));
        player.inventoryFull = true;
        catchOnce();
        assertEquals(1, player.messages.size());
        assertTrue(player.messages.get(0).contains("Inventario pieno"));
    }

    // ------------------------------------------------------------------ nessun duplicato

    @Test
    public void everySuccessfulCatchGivesExactlyOneReward() throws IOException {
        configure(categories(true, true, true) + COMMAND_REWARDS);
        int fish = 0;
        int armor = 0;
        int commands = 0;
        int catches = 3000;
        for (int i = 0; i < catches; i++) {
            player.inventoryFull = i % 2 == 0;
            int inventoryBefore = player.inventory.size();
            int droppedBefore = world.dropped.size();
            int dispatchedBefore = server.dispatched.size();

            FakeCaughtItem caught = catchOnce();

            int items = (player.inventory.size() - inventoryBefore) + (world.dropped.size() - droppedBefore);
            int commandRuns = server.dispatched.size() - dispatchedBefore;
            if (!caught.isRemoved()) {
                // Pesce: il bottino originale e' stato sostituito da un solo pesce e nient'altro.
                assertEquals(1, caught.setStackCalls);
                assertEquals(Material.RAW_FISH, caught.stack.getType());
                assertEquals(1, caught.stack.getAmount());
                assertEquals(0, items);
                assertEquals(0, commandRuns);
                fish++;
            } else {
                assertEquals(1, caught.removeCalls);
                assertEquals(0, caught.setStackCalls);
                if (items == 1) {
                    assertEquals(0, commandRuns);
                    armor++;
                } else {
                    assertEquals(0, items);
                    assertEquals(2, commandRuns); // entrambi i comandi dell'unica ricompensa, una sola volta
                    commands++;
                }
            }
        }
        assertEquals(catches, fish + armor + commands);
        assertTrue(fish > 0 && armor > 0 && commands > 0);
        assertEquals(armor, player.inventory.size() + world.dropped.size());
    }

    // ------------------------------------------------------------------ helper

    private void configure(String yaml) throws IOException {
        write(yaml);
        List<String> errors = configManager.reload().errors();
        assertTrue("Errori inattesi: " + errors, errors.isEmpty());
        player.messages.clear();
        player.inventory.clear();
        player.inventoryFull = false;
        world.dropped.clear();
        server.dispatched.clear();
    }

    private void write(String yaml) throws IOException {
        Files.write(configFile.toPath(), yaml.getBytes(StandardCharsets.UTF_8));
    }

    private FakeCaughtItem catchOnce() {
        FakeCaughtItem caught = new FakeCaughtItem(Fakes.vanillaLoot());
        PlayerFishEvent event = new PlayerFishEvent(player.proxy, caught.proxy, null, PlayerFishEvent.State.CAUGHT_FISH);
        bus.call(event);
        assertSame(caught.proxy, event.getCaught());
        return caught;
    }

    private void assertUntouched(FakeCaughtItem caught) {
        assertEquals(0, caught.removeCalls);
        assertEquals(0, caught.setStackCalls);
        assertEquals(Material.BOW, caught.stack.getType());
    }

    private void assertNoRewardGiven() {
        assertTrue(player.inventory.isEmpty());
        assertTrue(world.dropped.isEmpty());
        assertTrue(server.dispatched.isEmpty());
        assertTrue(player.messages.isEmpty());
    }

    private int countLogs(Level level, String text) {
        int count = 0;
        for (LogRecord record : log.records) {
            if (record.getLevel().equals(level) && record.getMessage().contains(text)) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void giveOrDropNeverDuplicates() {
        Fakes.server();
        ItemStack item = new ItemStack(Material.DIAMOND_BOOTS, 1);
        assertFalse(RewardDelivery.giveOrDrop(player.proxy, item));
        assertEquals(1, player.inventory.size());
        assertTrue(world.dropped.isEmpty());

        player.inventoryFull = true;
        assertTrue(RewardDelivery.giveOrDrop(player.proxy, new ItemStack(Material.DIAMOND_HELMET, 1)));
        assertEquals(1, player.inventory.size());
        assertEquals(1, world.dropped.size());
        assertEquals(Material.DIAMOND_HELMET, world.dropped.get(0).getType());
    }
}
