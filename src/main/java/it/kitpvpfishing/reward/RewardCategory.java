package it.kitpvpfishing.reward;

/**
 * Categorie di ricompensa estratte alla prima fase.
 */
public enum RewardCategory {
    FISH("fish", "Pesce", 80.0D),
    ARMOR("armor", "Armatura", 15.0D),
    COMMANDS("commands", "Comandi", 5.0D);

    private final String key;
    private final String displayName;
    private final double defaultWeight;

    RewardCategory(String key, String displayName, double defaultWeight) {
        this.key = key;
        this.displayName = displayName;
        this.defaultWeight = defaultWeight;
    }

    /** Chiave usata in {@code categories.<chiave>} nel config. */
    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    public static RewardCategory byKey(String key) {
        for (RewardCategory category : values()) {
            if (category.key.equalsIgnoreCase(key)) {
                return category;
            }
        }
        return null;
    }

    public static String keys() {
        StringBuilder builder = new StringBuilder();
        for (RewardCategory category : values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(category.key);
        }
        return builder.toString();
    }
}
