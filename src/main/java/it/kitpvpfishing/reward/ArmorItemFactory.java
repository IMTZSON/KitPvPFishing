package it.kitpvpfishing.reward;

import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.util.Text;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Crea il singolo pezzo di armatura di diamante con Protezione e Indistruttibilita' (Unbreaking).
 * I livelli vengono applicati ignorando i limiti vanilla; l'oggetto mantiene la normale durabilita'.
 */
public final class ArmorItemFactory {

    private ArmorItemFactory() {
    }

    public static ItemStack create(PluginSettings settings, RolledReward reward, String playerName) {
        if (reward.category() != RewardCategory.ARMOR) {
            throw new IllegalArgumentException("La ricompensa non e' un'armatura: " + reward);
        }
        String[] placeholders = placeholders(settings, reward, playerName);

        ItemStack item = new ItemStack(reward.piece().material(), 1);
        ItemMeta meta = item.getItemMeta();
        String name = settings.armorName();
        if (name != null && !name.isEmpty()) {
            meta.setDisplayName(Text.color(Text.apply(name, placeholders)));
        }
        List<String> lore = Text.apply(settings.armorLore(), placeholders);
        if (!lore.isEmpty()) {
            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, Text.color(lore.get(i)));
            }
            meta.setLore(lore);
        }
        // true = ignora il livello massimo vanilla (Protezione IV / Indistruttibilita' III).
        meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, reward.protectionLevel(), true);
        meta.addEnchant(Enchantment.DURABILITY, reward.unbreakingLevel(), true);
        if (settings.hideEnchants()) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Placeholder disponibili nel nome, nella lore e nel messaggio dell'armatura. */
    public static String[] placeholders(PluginSettings settings, RolledReward reward, String playerName) {
        int protection = reward.protectionLevel();
        int unbreaking = reward.unbreakingLevel();
        return new String[]{
                "%piece%", settings.pieceName(reward.piece()),
                "%protection%", String.valueOf(protection),
                "%unbreaking%", String.valueOf(unbreaking),
                "%protection_roman%", Text.roman(protection),
                "%unbreaking_roman%", Text.roman(unbreaking),
                "%player%", playerName
        };
    }
}
