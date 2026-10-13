package it.kitpvpfishing.config;

import it.kitpvpfishing.reward.ArmorPiece;
import it.kitpvpfishing.reward.CommandReward;
import it.kitpvpfishing.reward.FishType;
import it.kitpvpfishing.reward.RewardCategory;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static it.kitpvpfishing.testutil.TestConfigs.COMMAND_REWARDS;
import static it.kitpvpfishing.testutil.TestConfigs.bundledConfig;
import static it.kitpvpfishing.testutil.TestConfigs.categories;
import static it.kitpvpfishing.testutil.TestConfigs.valid;
import static it.kitpvpfishing.testutil.TestConfigs.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SettingsLoaderTest {

    // ------------------------------------------------------------------ config di esempio

    @Test
    public void bundledConfigIsValid() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(bundledConfig());
        assertTrue(result.report().errors().toString(), result.report().errors().isEmpty());
        PluginSettings settings = result.settings();

        assertTrue(settings.enabled());
        assertTrue(settings.worlds().isEmpty());
        assertEquals(80.0, settings.configuredCategoryWeights().get(RewardCategory.FISH), 0);
        assertEquals(15.0, settings.configuredCategoryWeights().get(RewardCategory.ARMOR), 0);
        assertEquals(5.0, settings.configuredCategoryWeights().get(RewardCategory.COMMANDS), 0);

        // L'esempio key_comune e' disattivato: la categoria comandi viene esclusa e le altre ricalcolate.
        assertTrue(settings.excludedCategories().containsKey(RewardCategory.COMMANDS));
        assertEquals(80.0 / 95.0, settings.categories().chance(RewardCategory.FISH), 1e-9);
        assertEquals(15.0 / 95.0, settings.categories().chance(RewardCategory.ARMOR), 1e-9);
        assertTrue(hasMessage(result.report().warnings(), "[categories.commands]"));

        assertEquals(Arrays.asList(4, 5, 6, 7, 8, 9, 10), settings.protectionLevels().values());
        assertEquals(Arrays.asList(3, 4, 5, 6, 7, 8, 9, 10), settings.unbreakingLevels().values());
        assertTrue(settings.protectionLevels().chance(4) > settings.protectionLevels().chance(10));
        assertEquals(4, settings.pieceTable().size());
        assertEquals(4, settings.fishTable().size());
        assertTrue(settings.messages().raw(MessageKey.REWARD_ARMOR).contains("Indistruttibilità"));
        assertTrue(settings.armorLore().get(1).contains("Indistruttibilità"));
    }

    @Test
    public void bundledConfigWithExampleEnabledWarnsAboutPlaceholderCommand() {
        String config = bundledConfig().replace("  key_comune:\n    enabled: false", "  key_comune:\n    enabled: true");
        SettingsLoader.Result result = SettingsLoader.loadFromString(config);
        assertTrue(result.report().errors().isEmpty());
        assertTrue(result.settings().categories().contains(RewardCategory.COMMANDS));
        assertEquals(0.05, result.settings().categories().chance(RewardCategory.COMMANDS), 1e-9);
        assertTrue(hasMessage(result.report().warnings(), "[command-rewards.key_comune.commands] contiene ancora il comando segnaposto"));
    }

    // ------------------------------------------------------------------ filtri sui livelli

    @Test
    public void onlyEnabledLevelsInRangeWithPositiveWeightAreEligible() {
        PluginSettings settings = valid(yaml(
                "armor:",
                "  enchantments:",
                "    protection:",
                "      min: 5",
                "      max: 9",
                "      default-weight: 0",
                "      levels:",
                "        4: {enabled: true, weight: 50}",   // sotto il minimo
                "        5: {enabled: false, weight: 10}",  // disattivato
                "        6: {enabled: true, weight: 0}",    // peso 0
                "        7: {enabled: true, weight: 3}",
                "        8: 4",                            // forma breve: solo peso
                "        12: {enabled: true, weight: 50}"  // sopra il massimo
        ));
        assertEquals(Arrays.asList(7, 8), settings.protectionLevels().values());
        assertEquals(3.0 / 7.0, settings.protectionLevels().chance(7), 1e-9);
    }

    @Test
    public void unlistedLevelsUseDefaultWeight() {
        PluginSettings settings = valid(yaml(
                "armor:",
                "  enchantments:",
                "    unbreaking:",
                "      min: 3",
                "      max: 6",
                "      default-weight: 2",
                "      levels:",
                "        4: {enabled: true, weight: 6}"));
        assertEquals(Arrays.asList(3, 4, 5, 6), settings.unbreakingLevels().values());
        assertEquals(2.0 / 12.0, settings.unbreakingLevels().chance(3), 1e-9);
        assertEquals(6.0 / 12.0, settings.unbreakingLevels().chance(4), 1e-9);
    }

    @Test
    public void levelsOutsideRangeProduceWarningWithPath() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml(
                "armor:",
                "  enchantments:",
                "    protection:",
                "      min: 4",
                "      max: 6",
                "      levels:",
                "        9: 10"));
        assertTrue(result.report().errors().isEmpty());
        assertTrue(hasMessage(result.report().warnings(), "[armor.enchantments.protection.levels.9] livello fuori dall'intervallo 4-6"));
    }

    @Test
    public void levelsAboveVanillaAreAllowedWithDisplayWarning() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml(
                "armor:",
                "  enchantments:",
                "    protection: {min: 10, max: 15}"));
        assertTrue(result.report().errors().isEmpty());
        assertEquals(Arrays.asList(10, 11, 12, 13, 14, 15), result.settings().protectionLevels().values());
        assertTrue(hasMessage(result.report().warnings(), "[armor.enchantments.protection] livelli oltre 10"));
    }

    @Test
    public void allLevelsDisabledExcludesArmorCategory() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml(
                "armor:",
                "  enchantments:",
                "    unbreaking:",
                "      min: 3",
                "      max: 4",
                "      levels:",
                "        3: {enabled: false, weight: 5}",
                "        4: {enabled: true, weight: 0}"));
        assertTrue(result.report().errors().isEmpty());
        PluginSettings settings = result.settings();
        assertTrue(settings.unbreakingLevels().isEmpty());
        assertFalse(settings.categories().contains(RewardCategory.ARMOR));
        assertTrue(settings.excludedCategories().get(RewardCategory.ARMOR).contains("indistruttibilita"));
        assertTrue(hasMessage(result.report().warnings(), "[armor.enchantments.unbreaking] nessun livello estraibile"));
    }

    // ------------------------------------------------------------------ configurazioni non valide

    @Test
    public void invalidValuesAreReportedWithExactPath() {
        assertError(yaml("enabled: forse"), "[enabled]");
        assertError(yaml("worlds: {a: b}"), "[worlds]");
        assertError(yaml("worlds: ['kitpvp', '  ']"), "[worlds[1]]");
        assertError(yaml("categories:", "  fish: {enabled: true, weight: abc}"), "[categories.fish.weight]");
        assertError(yaml("categories:", "  pesca: {enabled: true, weight: 3}"), "[categories.pesca]");
        assertError(yaml("categories:", "  armor: [1, 2]"), "[categories.armor]");
        assertError(yaml("fish:", "  types:", "    tuna: 5"), "[fish.types.tuna]");
        assertError(yaml("armor:", "  pieces:", "    helmet: {enabled: true, weight: -5}"), "[armor.pieces.helmet.weight]");
        assertError(yaml("armor:", "  pieces:", "    gloves: {enabled: true, weight: 5}"), "[armor.pieces.gloves]");
        assertError(yaml("armor:", "  enchantments:", "    protection: {min: 8, max: 5}"), "[armor.enchantments.protection] min (8) e' maggiore di max (5)");
        assertError(yaml("armor:", "  enchantments:", "    unbreaking: {min: 0, max: 5}"), "[armor.enchantments.unbreaking.min]");
        assertError(yaml("armor:", "  enchantments:", "    unbreaking: {min: 3, max: 40000}"), "[armor.enchantments.unbreaking.max]");
        assertError(yaml("armor:", "  enchantments:", "    unbreaking: {min: 3.5, max: 5}"), "[armor.enchantments.unbreaking.min]");
        assertError(yaml("armor:", "  enchantments:", "    protection:", "      levels:", "        dieci: 5"),
                "[armor.enchantments.protection.levels.dieci]");
        assertError(yaml("armor:", "  enchantments:", "    protection:", "      levels:", "        0: 5"),
                "[armor.enchantments.protection.levels.0]");
        assertError(yaml("armor:", "  enchantments:", "    protection:", "      levels:", "        5: {enabled: true, weight: molto}"),
                "[armor.enchantments.protection.levels.5.weight]");
        assertError(yaml("armor:", "  item:", "    lore: {a: b}"), "[armor.item.lore]");
        assertError(yaml("command-rewards:", "  broken: 5"), "[command-rewards.broken]");
        assertError(yaml("command-rewards:", "  vip:", "    enabled: true", "    weight: 5"), "[command-rewards.vip.commands]");
        assertError(yaml("command-rewards:", "  vip:", "    commands: ['say ciao', '  ', '/']"), "[command-rewards.vip.commands[1]]");
        assertError(yaml("command-rewards:", "  vip:", "    commands: ['say ciao', {a: b}]"), "[command-rewards.vip.commands[1]]");
        assertError(yaml("messages:", "  prefix: [1, 2]"), "[messages.prefix]");
    }

    @Test
    public void yamlSyntaxErrorIsReported() {
        assertError(yaml("enabled: true", "categories:", "  fish: {enabled: true, weight: 80", "  armor: ["), "[config.yml] sintassi YAML non valida");
    }

    @Test
    public void allErrorsAreCollectedAtOnce() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml(
                "enabled: forse",
                "armor:",
                "  pieces:",
                "    gloves: 5",
                "  enchantments:",
                "    protection: {min: 9, max: 2}"));
        assertNull(result.settings());
        assertEquals(3, result.report().errorCount());
    }

    // ------------------------------------------------------------------ categorie e ricompense

    @Test
    public void invalidCategoriesAreExcludedAndChancesRecalculated() {
        PluginSettings settings = valid(categories(true, true, true) + COMMAND_REWARDS + yaml(
                "armor:",
                "  pieces:",
                "    helmet: {enabled: false, weight: 25}",
                "    boots: {enabled: true, weight: 0}"));
        assertFalse(settings.categories().contains(RewardCategory.ARMOR));
        assertEquals(80.0 / 85.0, settings.categories().chance(RewardCategory.FISH), 1e-9);
        assertEquals(5.0 / 85.0, settings.categories().chance(RewardCategory.COMMANDS), 1e-9);
    }

    @Test
    public void disabledOrZeroWeightCategoriesAreExcluded() {
        PluginSettings settings = valid(yaml(
                "categories:",
                "  fish: {enabled: false, weight: 80}",
                "  armor: {enabled: true, weight: 0}",
                "  commands: {enabled: true, weight: 5}") + COMMAND_REWARDS);
        assertEquals(Arrays.asList(RewardCategory.COMMANDS), settings.categories().values());
        assertEquals("disattivata", settings.excludedCategories().get(RewardCategory.FISH));
        assertEquals("peso 0", settings.excludedCategories().get(RewardCategory.ARMOR));
    }

    @Test
    public void noValidCategoryProducesWarning() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(categories(false, false, true));
        assertTrue(result.report().errors().isEmpty());
        assertTrue(result.settings().categories().isEmpty());
        assertTrue(hasMessage(result.report().warnings(), "[categories] nessuna categoria di ricompensa valida"));
    }

    @Test
    public void commandRewardsAreParsed() {
        PluginSettings settings = valid(categories(false, false, true) + COMMAND_REWARDS);
        assertEquals(1, settings.commandRewards().size());
        CommandReward reward = settings.commandRewards().values().get(0);
        assertEquals("vip_key", reward.id());
        assertEquals("&6Key VIP", reward.displayName());
        // La "/" iniziale viene rimossa.
        assertEquals(Arrays.asList("givekey %player% vip 1", "eco give %player% 100"), reward.commands());
    }

    @Test
    public void rewardWithoutMessageUsesDefaultAndEmptyMessageDisablesIt() {
        PluginSettings settings = valid(categories(false, false, true) + yaml(
                "command-rewards:",
                "  a: {commands: ['say a']}",
                "  b: {message: '', commands: ['say b']}"));
        for (CommandReward reward : settings.commandRewards().values()) {
            if (reward.id().equals("a")) {
                assertNull(reward.message());
            } else {
                assertEquals("", reward.message());
            }
        }
    }

    @Test
    public void unknownFishAndPieceKeysAreValidatedAndDisplayNamesRead() {
        PluginSettings settings = valid(yaml(
                "fish:",
                "  types:",
                "    salmon: {enabled: true, weight: 3, display-name: 'Salmone'}",
                "    cod: {enabled: false, weight: 60}",
                "armor:",
                "  pieces:",
                "    boots: {enabled: true, weight: 1, display-name: '&bStivaloni'}"));
        assertEquals(Arrays.asList(FishType.SALMON), settings.fishTable().values());
        assertEquals("Salmone", settings.fishName(FishType.SALMON));
        assertEquals(Arrays.asList(ArmorPiece.BOOTS), settings.pieceTable().values());
        assertEquals("&bStivaloni", settings.pieceName(ArmorPiece.BOOTS));
    }

    @Test
    public void worldsAreCaseInsensitiveAndEmptyMeansAll() {
        PluginSettings all = valid(yaml("worlds: []"));
        assertTrue(all.isWorldAllowed("qualsiasi"));
        PluginSettings some = valid(yaml("worlds: ['KitPvP', 'arena']"));
        assertTrue(some.isWorldAllowed("kitpvp"));
        assertTrue(some.isWorldAllowed("ARENA"));
        assertFalse(some.isWorldAllowed("world"));
    }

    @Test
    public void messagesFallBackToDefaultsAndCanBeDisabled() {
        PluginSettings settings = valid(yaml(
                "messages:",
                "  reward-messages-enabled: false",
                "  reward-fish: '&aPesce! %item%'"));
        assertFalse(settings.messages().rewardMessagesEnabled());
        assertEquals("&aPesce! %item%", settings.messages().raw(MessageKey.REWARD_FISH));
        assertEquals(MessageKey.INVENTORY_FULL.defaultValue(), settings.messages().raw(MessageKey.INVENTORY_FULL));
    }

    @Test
    public void unknownKeysOnlyWarn() {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml("enabeld: true"));
        assertTrue(result.report().errors().isEmpty());
        assertTrue(hasMessage(result.report().warnings(), "[enabeld] chiave sconosciuta"));
    }

    // ------------------------------------------------------------------ helper

    private static void assertError(String yaml, String expected) {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml);
        assertNull("Con errori la configurazione non deve essere applicata", result.settings());
        if (!hasMessage(result.report().errors(), expected)) {
            fail("Errore atteso contenente '" + expected + "', trovati: " + result.report().errors());
        }
    }

    private static boolean hasMessage(List<String> messages, String expected) {
        for (String message : messages) {
            if (message.contains(expected)) {
                return true;
            }
        }
        return false;
    }
}
