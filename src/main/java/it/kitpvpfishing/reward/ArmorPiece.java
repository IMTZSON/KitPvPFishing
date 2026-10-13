package it.kitpvpfishing.reward;

import org.bukkit.Material;

/**
 * Pezzi di armatura di diamante pescabili (uno solo per cattura).
 */
public enum ArmorPiece {
    HELMET("helmet", Material.DIAMOND_HELMET, "Elmo di diamante"),
    CHESTPLATE("chestplate", Material.DIAMOND_CHESTPLATE, "Corazza di diamante"),
    LEGGINGS("leggings", Material.DIAMOND_LEGGINGS, "Gambali di diamante"),
    BOOTS("boots", Material.DIAMOND_BOOTS, "Stivali di diamante");

    private final String key;
    private final Material material;
    private final String defaultName;

    ArmorPiece(String key, Material material, String defaultName) {
        this.key = key;
        this.material = material;
        this.defaultName = defaultName;
    }

    /** Chiave usata in {@code armor.pieces.<chiave>} nel config. */
    public String key() {
        return key;
    }

    public Material material() {
        return material;
    }

    public String defaultName() {
        return defaultName;
    }

    public static ArmorPiece byKey(String key) {
        for (ArmorPiece piece : values()) {
            if (piece.key.equalsIgnoreCase(key)) {
                return piece;
            }
        }
        return null;
    }

    public static String keys() {
        StringBuilder builder = new StringBuilder();
        for (ArmorPiece piece : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(piece.key);
        }
        return builder.toString();
    }
}
