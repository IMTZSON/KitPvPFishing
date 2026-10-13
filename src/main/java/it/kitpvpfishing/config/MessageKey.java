package it.kitpvpfishing.config;

/**
 * Messaggi personalizzabili nella sezione {@code messages} del config, con il testo predefinito.
 * Un messaggio impostato a stringa vuota non viene inviato.
 */
public enum MessageKey {
    PREFIX("prefix", "&8[&bPesca&8] &r"),
    REWARD_FISH("reward-fish", "&7Hai pescato: &f%item%&7."),
    REWARD_ARMOR("reward-armor", "&7Hai pescato &b%piece% &7(Protezione &a%protection%&7, Indistruttibilità &a%unbreaking%&7)!"),
    REWARD_COMMAND_DEFAULT("reward-command-default", "&7Hai pescato: %reward%&7!"),
    INVENTORY_FULL("inventory-full", "&cInventario pieno! &7L'oggetto è stato lasciato a terra vicino a te."),
    NO_PERMISSION("no-permission", "&cNon hai il permesso per usare questo comando."),
    USAGE("usage", "&7Uso: &f/%label% reload &7oppure &f/%label% chances"),
    RELOAD_SUCCESS("reload-success", "&aConfigurazione ricaricata. &7Avvisi: &e%warnings%"),
    RELOAD_FAILED("reload-failed", "&cConfigurazione non valida (&e%errors% &cerrori): resta attiva l'ultima configurazione valida.");

    private final String path;
    private final String defaultValue;

    MessageKey(String path, String defaultValue) {
        this.path = path;
        this.defaultValue = defaultValue;
    }

    /** Chiave sotto {@code messages.} nel config. */
    public String path() {
        return path;
    }

    public String defaultValue() {
        return defaultValue;
    }

    /** I messaggi di ricompensa possono essere disattivati tutti insieme. */
    public boolean isRewardMessage() {
        return this == REWARD_FISH || this == REWARD_ARMOR || this == REWARD_COMMAND_DEFAULT;
    }

    public static MessageKey byPath(String path) {
        for (MessageKey key : values()) {
            if (key.path.equals(path)) {
                return key;
            }
        }
        return null;
    }
}
