package it.kitpvpfishing.reward;

import it.kitpvpfishing.config.PluginSettings;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static it.kitpvpfishing.reward.WeightedTableTest.assertClose;
import static it.kitpvpfishing.testutil.TestConfigs.COMMAND_REWARDS;
import static it.kitpvpfishing.testutil.TestConfigs.bundledConfig;
import static it.kitpvpfishing.testutil.TestConfigs.categories;
import static it.kitpvpfishing.testutil.TestConfigs.valid;
import static it.kitpvpfishing.testutil.TestConfigs.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RewardRollerTest {

    @Test
    public void categoriesFollowConfiguredWeights() {
        PluginSettings settings = valid(categories(true, true, true) + COMMAND_REWARDS);
        int draws = 300000;
        int[] counts = new int[RewardCategory.values().length];
        Random random = new Random(11);
        for (int i = 0; i < draws; i++) {
            RolledReward reward = RewardRoller.roll(settings, random);
            counts[reward.category().ordinal()]++;
            assertExactlyOneCategoryFilled(reward);
        }
        assertClose(0.80, counts[RewardCategory.FISH.ordinal()] / (double) draws, 0.005);
        assertClose(0.15, counts[RewardCategory.ARMOR.ordinal()] / (double) draws, 0.005);
        assertClose(0.05, counts[RewardCategory.COMMANDS.ordinal()] / (double) draws, 0.005);
    }

    @Test
    public void disabledCommandRewardIsNeverPicked() {
        PluginSettings settings = valid(categories(false, false, true) + COMMAND_REWARDS);
        Random random = new Random(3);
        for (int i = 0; i < 20000; i++) {
            assertEquals("vip_key", RewardRoller.roll(settings, random).commandReward().id());
        }
    }

    @Test
    public void protectionAndUnbreakingAreIndependent() {
        // Pesi uguali su 4..10 e 3..10: ogni combinazione deve uscire con probabilita' 1/7 * 1/8.
        PluginSettings settings = valid(categories(false, true, false) + yaml(
                "armor:",
                "  enchantments:",
                "    protection: {min: 4, max: 10}",
                "    unbreaking: {min: 3, max: 10}"));
        int draws = 400000;
        int[][] joint = new int[11][11];
        int[] protection = new int[11];
        int[] unbreaking = new int[11];
        Random random = new Random(99);
        for (int i = 0; i < draws; i++) {
            RolledReward reward = RewardRoller.roll(settings, random);
            joint[reward.protectionLevel()][reward.unbreakingLevel()]++;
            protection[reward.protectionLevel()]++;
            unbreaking[reward.unbreakingLevel()]++;
        }
        // Le combinazioni "estreme" richieste sono possibili.
        assertTrue(joint[4][10] > 0);
        assertTrue(joint[10][3] > 0);
        for (int p = 4; p <= 10; p++) {
            assertClose(1.0 / 7.0, protection[p] / (double) draws, 0.005);
            for (int u = 3; u <= 10; u++) {
                // P(p, u) = P(p) * P(u)  (indipendenza)
                double expected = (protection[p] / (double) draws) * (unbreaking[u] / (double) draws);
                assertClose(expected, joint[p][u] / (double) draws, 0.0025);
            }
        }
        for (int u = 3; u <= 10; u++) {
            assertClose(1.0 / 8.0, unbreaking[u] / (double) draws, 0.005);
        }
    }

    @Test
    public void onlyEligibleLevelsAreEverRolled() {
        PluginSettings settings = valid(categories(false, true, false) + yaml(
                "armor:",
                "  enchantments:",
                "    protection:",
                "      min: 4",
                "      max: 10",
                "      default-weight: 0",
                "      levels:",
                "        5: 1",
                "        9: {enabled: true, weight: 1}",
                "        10: {enabled: false, weight: 100}",
                "    unbreaking:",
                "      min: 6",
                "      max: 7"));
        Set<Integer> protection = new HashSet<Integer>();
        Set<Integer> unbreaking = new HashSet<Integer>();
        Random random = new Random(5);
        for (int i = 0; i < 50000; i++) {
            RolledReward reward = RewardRoller.roll(settings, random);
            protection.add(reward.protectionLevel());
            unbreaking.add(reward.unbreakingLevel());
        }
        assertEquals(new HashSet<Integer>(java.util.Arrays.asList(5, 9)), protection);
        assertEquals(new HashSet<Integer>(java.util.Arrays.asList(6, 7)), unbreaking);
    }

    @Test
    public void higherLevelsAreRarerWithBundledWeights() {
        PluginSettings settings = valid(bundledConfig());
        int[] protection = new int[11];
        Random random = new Random(8);
        int armorRolls = 0;
        while (armorRolls < 100000) {
            RolledReward reward = RewardRoller.roll(settings, random);
            if (reward.category() == RewardCategory.ARMOR) {
                armorRolls++;
                protection[reward.protectionLevel()]++;
            }
        }
        for (int level = 4; level < 10; level++) {
            assertTrue("livello " + level + " deve essere piu' frequente di " + (level + 1), protection[level] > protection[level + 1]);
        }
        assertClose(0.30, protection[4] / (double) armorRolls, 0.01);
        assertClose(0.02, protection[10] / (double) armorRolls, 0.005);
    }

    @Test
    public void exactlyOneArmorPiecePerRoll() {
        PluginSettings settings = valid(categories(false, true, false));
        Random random = new Random(1);
        int[] pieces = new int[ArmorPiece.values().length];
        for (int i = 0; i < 40000; i++) {
            RolledReward reward = RewardRoller.roll(settings, random);
            assertNotNull(reward.piece());
            pieces[reward.piece().ordinal()]++;
        }
        for (int count : pieces) {
            assertClose(0.25, count / 40000.0, 0.01);
        }
    }

    @Test
    public void noValidCategoryMeansNoReward() {
        PluginSettings settings = valid(categories(false, false, false));
        assertNull(RewardRoller.roll(settings, new Random(1)));
    }

    private static void assertExactlyOneCategoryFilled(RolledReward reward) {
        int filled = (reward.fish() != null ? 1 : 0) + (reward.piece() != null ? 1 : 0) + (reward.commandReward() != null ? 1 : 0);
        assertEquals(1, filled);
    }
}
