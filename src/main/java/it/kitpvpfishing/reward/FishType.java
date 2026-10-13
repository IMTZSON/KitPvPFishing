package it.kitpvpfishing.reward;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Pesci della 1.8: tutti usano {@link Material#RAW_FISH} con un data value diverso.
 * I pesi predefiniti riproducono le proporzioni vanilla 1.8 (60/25/2/13).
 */
public enum FishType {
    COD("cod", (short) 0, "Pesce crudo", 60.0D),
    SALMON("salmon", (short) 1, "Salmone crudo", 25.0D),
    CLOWNFISH("clownfish", (short) 2, "Pesce pagliaccio", 2.0D),
    PUFFERFISH("pufferfish", (short) 3, "Pesce palla", 13.0D);

    private final String key;
    private final short data;
    private final String defaultName;
    private final double defaultWeight;

    FishType(String key, short data, String defaultName, double defaultWeight) {
        this.key = key;
        this.data = data;
        this.defaultName = defaultName;
        this.defaultWeight = defaultWeight;
    }

    /** Chiave usata in {@code fish.types.<chiave>} nel config. */
    public String key() {
        return key;
    }

    public short data() {
        return data;
    }

    public String defaultName() {
        return defaultName;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    /** Crea esattamente un pesce. */
    public ItemStack createItem() {
        return new ItemStack(Material.RAW_FISH, 1, data);
    }

    public static FishType byKey(String key) {
        for (FishType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }

    public static String keys() {
        StringBuilder builder = new StringBuilder();
        for (FishType type : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(type.key);
        }
        return builder.toString();
    }
}
