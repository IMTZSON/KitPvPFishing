package it.kitpvpfishing.reward;

import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.testutil.Fakes;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static it.kitpvpfishing.testutil.TestConfigs.bundledConfig;
import static it.kitpvpfishing.testutil.TestConfigs.valid;
import static it.kitpvpfishing.testutil.TestConfigs.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ArmorItemFactoryTest {

    @Before
    public void setUp() {
        Fakes.server();
    }

    @Test
    public void appliesBothEnchantmentsBeyondVanillaLimits() {
        PluginSettings settings = valid(bundledConfig());
        ItemStack item = ArmorItemFactory.create(settings, RolledReward.armor(ArmorPiece.HELMET, 10, 3), "Steve");
        assertEquals(Material.DIAMOND_HELMET, item.getType());
        assertEquals(1, item.getAmount());
        Fakes.MetaState meta = Fakes.state(item.getItemMeta());
        assertEquals(Integer.valueOf(10), meta.enchants.get(Enchantment.PROTECTION_ENVIRONMENTAL));
        assertEquals(Integer.valueOf(3), meta.enchants.get(Enchantment.DURABILITY));
        assertEquals(2, meta.enchants.size());
        // Durabilita' normale: nessun danno/stato "infinito" applicato.
        assertEquals(0, item.getDurability());

        ItemStack other = ArmorItemFactory.create(settings, RolledReward.armor(ArmorPiece.BOOTS, 4, 10), "Steve");
        Fakes.MetaState otherMeta = Fakes.state(other.getItemMeta());
        assertEquals(Integer.valueOf(4), otherMeta.enchants.get(Enchantment.PROTECTION_ENVIRONMENTAL));
        assertEquals(Integer.valueOf(10), otherMeta.enchants.get(Enchantment.DURABILITY));
    }

    @Test
    public void nameAndLoreUseColorsAndPlaceholders() {
        PluginSettings settings = valid(bundledConfig());
        ItemStack item = ArmorItemFactory.create(settings, RolledReward.armor(ArmorPiece.CHESTPLATE, 7, 9), "Steve");
        Fakes.MetaState meta = Fakes.state(item.getItemMeta());
        assertEquals(color("&bCorazza di diamante"), meta.displayName);
        assertEquals(Arrays.asList(
                color("&7Protezione: &aVII"),
                color("&7Indistruttibilità: &aIX"),
                color("&8Pescato da Steve")), meta.lore);
        assertFalse(meta.flags.contains(ItemFlag.HIDE_ENCHANTS));
    }

    @Test
    public void customTemplateAndHiddenEnchantments() {
        PluginSettings settings = valid(yaml(
                "armor:",
                "  enchantments:",
                "    protection: {min: 15, max: 15}",
                "  item:",
                "    name: '&c%piece% P%protection% U%unbreaking%'",
                "    lore: ['%protection_roman%/%unbreaking_roman%']",
                "    hide-enchants: true"));
        ItemStack item = ArmorItemFactory.create(settings, RolledReward.armor(ArmorPiece.LEGGINGS, 15, 4), "Alex");
        Fakes.MetaState meta = Fakes.state(item.getItemMeta());
        assertEquals(color("&cGambali di diamante P15 U4"), meta.displayName);
        assertEquals(Arrays.asList("XV/IV"), meta.lore);
        assertTrue(meta.flags.contains(ItemFlag.HIDE_ENCHANTS));
        assertEquals(Integer.valueOf(15), meta.enchants.get(Enchantment.PROTECTION_ENVIRONMENTAL));
    }

    @Test
    public void emptyNameAndLoreKeepVanillaAppearance() {
        PluginSettings settings = valid(yaml("armor:", "  item:", "    name: ''", "    lore: []"));
        ItemStack item = ArmorItemFactory.create(settings, RolledReward.armor(ArmorPiece.BOOTS, 5, 5), "Alex");
        Fakes.MetaState meta = Fakes.state(item.getItemMeta());
        assertNull(meta.displayName);
        assertNull(meta.lore);
        assertEquals(2, meta.enchants.size());
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
