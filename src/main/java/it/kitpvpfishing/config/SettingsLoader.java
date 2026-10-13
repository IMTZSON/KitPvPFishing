package it.kitpvpfishing.config;

import it.kitpvpfishing.reward.ArmorPiece;
import it.kitpvpfishing.reward.CommandReward;
import it.kitpvpfishing.reward.FishType;
import it.kitpvpfishing.reward.RewardCategory;
import it.kitpvpfishing.reward.WeightedTable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Legge e valida il config, producendo una {@link PluginSettings} oppure l'elenco degli errori.
 * Non dipende dal server: puo' essere usato anche nei test.
 */
public final class SettingsLoader {

    /** Livello minimo accettato per un incantesimo. */
    public static final int MIN_ENCHANT_LEVEL = 1;
    /** Livello massimo memorizzabile nel NBT della 1.8 (short). */
    public static final int MAX_ENCHANT_LEVEL = Short.MAX_VALUE;
    /** Oltre questo livello il client 1.8.9 non ha la traduzione del numero romano. */
    public static final int CLIENT_DISPLAY_MAX_LEVEL = 10;
    /** Testo usato nell'esempio del config per indicare un comando da sostituire. */
    public static final String PLACEHOLDER_MARKER = "SOSTITUISCI";

    static final String DEFAULT_ARMOR_NAME = "&b%piece%";
    static final List<String> DEFAULT_ARMOR_LORE = Collections.unmodifiableList(Arrays.asList(
            "&7Protezione: &a%protection_roman%",
            "&7Indistruttibilità: &a%unbreaking_roman%"));

    private static final Set<String> ROOT_KEYS = keys("enabled", "debug", "worlds", "categories", "fish", "armor", "command-rewards", "messages");
    private static final Set<String> ENTRY_KEYS = keys("enabled", "weight");
    private static final Set<String> NAMED_ENTRY_KEYS = keys("enabled", "weight", "display-name");
    private static final Set<String> FISH_KEYS = keys("types");
    private static final Set<String> ARMOR_KEYS = keys("pieces", "enchantments", "item");
    private static final Set<String> ENCHANTMENTS_KEYS = keys("protection", "unbreaking");
    private static final Set<String> ENCHANT_KEYS = keys("min", "max", "default-weight", "levels");
    private static final Set<String> ITEM_KEYS = keys("name", "lore", "hide-enchants");
    private static final Set<String> REWARD_KEYS = keys("enabled", "weight", "display-name", "message", "commands");

    private final ValidationReport report = new ValidationReport();
    private final PluginSettings settings = new PluginSettings();

    private SettingsLoader() {
    }

    /** Analizza un testo YAML (gli errori di sintassi diventano errori di validazione). */
    public static Result loadFromString(String yaml) {
        String text = yaml == null ? "" : yaml;
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            ValidationReport report = new ValidationReport();
            report.error("config.yml", "sintassi YAML non valida: " + e.getMessage());
            return new Result(null, report);
        }
        Result result = load(config);
        if (text.trim().isEmpty()) {
            result.report().warning("config.yml", "il file e' vuoto: vengono usati i valori predefiniti");
        }
        return result;
    }

    public static Result load(ConfigurationSection root) {
        SettingsLoader loader = new SettingsLoader();
        loader.loadAll(root);
        return new Result(loader.report.hasErrors() ? null : loader.settings, loader.report);
    }

    // ------------------------------------------------------------------ sezioni

    private void loadAll(ConfigurationSection root) {
        warnUnknownKeys(root, ROOT_KEYS);
        settings.enabled = readBoolean(root, "enabled", true);
        settings.debug = readBoolean(root, "debug", false);
        settings.worlds = readWorlds(root);

        Map<RewardCategory, EntrySpec> categories = loadCategories(root);
        loadFish(root);
        loadArmor(root);
        loadCommandRewards(root);
        loadMessages(root);
        buildCategoryTable(categories);
    }

    private Set<String> readWorlds(ConfigurationSection root) {
        List<String> raw = readStringList(root, "worlds", Collections.<String>emptyList());
        Set<String> worlds = new LinkedHashSet<String>();
        for (int i = 0; i < raw.size(); i++) {
            String world = raw.get(i).trim();
            if (world.isEmpty()) {
                report.error("worlds[" + i + "]", "nome del mondo vuoto");
            } else {
                worlds.add(world.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(worlds);
    }

    private Map<RewardCategory, EntrySpec> loadCategories(ConfigurationSection root) {
        Map<RewardCategory, EntrySpec> result = new EnumMap<RewardCategory, EntrySpec>(RewardCategory.class);
        ConfigurationSection section = readSection(root, "categories");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                if (RewardCategory.byKey(key) == null) {
                    report.error(path(section, key), "categoria sconosciuta (ammesse: " + RewardCategory.keys() + ")");
                }
            }
        }
        for (RewardCategory category : RewardCategory.values()) {
            if (section == null || !section.contains(category.key())) {
                report.warning("categories." + category.key(), "sezione mancante: categoria attiva con peso predefinito "
                        + (long) category.defaultWeight());
                result.put(category, EntrySpec.defaults(category.defaultWeight()));
            } else {
                EntrySpec spec = readEntry(section, category.key(), category.defaultWeight(), ENTRY_KEYS);
                result.put(category, spec);
            }
        }
        return result;
    }

    private void loadFish(ConfigurationSection root) {
        ConfigurationSection fish = readSection(root, "fish");
        if (fish != null) {
            warnUnknownKeys(fish, FISH_KEYS);
        }
        ConfigurationSection types = fish == null ? null : readSection(fish, "types");
        WeightedTable.Builder<FishType> table = WeightedTable.builder();
        if (types == null) {
            report.warning("fish.types", "sezione mancante: vengono usati i pesci e i pesi predefiniti");
            for (FishType type : FishType.values()) {
                table.add(type, type.defaultWeight());
            }
        } else {
            for (String key : types.getKeys(false)) {
                FishType type = FishType.byKey(key);
                if (type == null) {
                    report.error(path(types, key), "tipo di pesce sconosciuto (ammessi: " + FishType.keys() + ")");
                    continue;
                }
                EntrySpec spec = readEntry(types, key, type.defaultWeight(), NAMED_ENTRY_KEYS);
                if (spec.section != null) {
                    settings.fishNames.put(type, readString(spec.section, "display-name", type.defaultName()));
                }
                if (spec.enabled) {
                    table.add(type, spec.weight);
                }
            }
        }
        settings.fishTable = table.build();
    }

    private void loadArmor(ConfigurationSection root) {
        ConfigurationSection armor = readSection(root, "armor");
        if (armor != null) {
            warnUnknownKeys(armor, ARMOR_KEYS);
        }

        ConfigurationSection pieces = armor == null ? null : readSection(armor, "pieces");
        WeightedTable.Builder<ArmorPiece> pieceTable = WeightedTable.builder();
        if (pieces == null) {
            report.warning("armor.pieces", "sezione mancante: tutti i pezzi attivi con peso 25");
            for (ArmorPiece piece : ArmorPiece.values()) {
                pieceTable.add(piece, 25.0D);
            }
        } else {
            for (String key : pieces.getKeys(false)) {
                ArmorPiece piece = ArmorPiece.byKey(key);
                if (piece == null) {
                    report.error(path(pieces, key), "pezzo di armatura sconosciuto (ammessi: " + ArmorPiece.keys() + ")");
                    continue;
                }
                EntrySpec spec = readEntry(pieces, key, 25.0D, NAMED_ENTRY_KEYS);
                if (spec.section != null) {
                    settings.pieceNames.put(piece, readString(spec.section, "display-name", piece.defaultName()));
                }
                if (spec.enabled) {
                    pieceTable.add(piece, spec.weight);
                }
            }
        }
        settings.pieceTable = pieceTable.build();

        ConfigurationSection enchantments = armor == null ? null : readSection(armor, "enchantments");
        if (enchantments != null) {
            warnUnknownKeys(enchantments, ENCHANTMENTS_KEYS);
        }
        settings.protectionLevels = loadEnchantment(enchantments, "protection", 4, 10);
        settings.unbreakingLevels = loadEnchantment(enchantments, "unbreaking", 3, 10);

        ConfigurationSection item = armor == null ? null : readSection(armor, "item");
        if (item == null) {
            settings.armorName = DEFAULT_ARMOR_NAME;
            settings.armorLore = DEFAULT_ARMOR_LORE;
            settings.hideEnchants = false;
        } else {
            warnUnknownKeys(item, ITEM_KEYS);
            settings.armorName = readString(item, "name", DEFAULT_ARMOR_NAME);
            settings.armorLore = Collections.unmodifiableList(new ArrayList<String>(readStringList(item, "lore", DEFAULT_ARMOR_LORE)));
            settings.hideEnchants = readBoolean(item, "hide-enchants", false);
        }
    }

    /**
     * Costruisce la tabella dei livelli di un incantesimo. Un livello e' estraibile solo se:
     * e' compreso tra min e max, e' attivo e ha peso positivo. I livelli dell'intervallo
     * non elencati in {@code levels} usano {@code default-weight}.
     */
    private WeightedTable<Integer> loadEnchantment(ConfigurationSection enchantments, String key, int defaultMin, int defaultMax) {
        String basePath = "armor.enchantments." + key;
        ConfigurationSection section = enchantments == null ? null : readSection(enchantments, key);
        if (section == null) {
            report.warning(basePath, "sezione mancante: livelli da " + defaultMin + " a " + defaultMax + " con pesi uguali");
            WeightedTable.Builder<Integer> builder = WeightedTable.builder();
            for (int level = defaultMin; level <= defaultMax; level++) {
                builder.add(level, 1.0D);
            }
            return builder.build();
        }
        warnUnknownKeys(section, ENCHANT_KEYS);

        int errorsBefore = report.errorCount();
        int min = readLevel(section, "min", defaultMin);
        int max = readLevel(section, "max", defaultMax);
        if (report.errorCount() == errorsBefore && min > max) {
            report.error(basePath, "min (" + min + ") e' maggiore di max (" + max + ")");
        }
        double defaultWeight = readWeight(section, "default-weight", 1.0D);

        Map<Integer, EntrySpec> listed = new TreeMap<Integer, EntrySpec>();
        ConfigurationSection levels = readSection(section, "levels");
        if (levels != null) {
            for (String levelKey : levels.getKeys(false)) {
                String levelPath = path(levels, levelKey);
                Integer level = parseLevelKey(levelKey);
                if (level == null) {
                    report.error(levelPath, "livello non valido: deve essere un intero tra "
                            + MIN_ENCHANT_LEVEL + " e " + MAX_ENCHANT_LEVEL);
                    continue;
                }
                EntrySpec spec = readEntry(levels, levelKey, defaultWeight, ENTRY_KEYS);
                if (listed.containsKey(level)) {
                    report.warning(levelPath, "livello " + level + " definito piu' volte: vale l'ultima definizione");
                }
                if (level < min || level > max) {
                    report.warning(levelPath, "livello fuori dall'intervallo " + min + "-" + max + ": ignorato");
                    continue;
                }
                listed.put(level, spec);
            }
        }
        if (report.errorCount() > errorsBefore) {
            return WeightedTable.empty();
        }

        WeightedTable.Builder<Integer> builder = WeightedTable.builder();
        boolean aboveClientDisplay = false;
        for (int level = min; level <= max; level++) {
            EntrySpec spec = listed.get(level);
            boolean enabled = spec == null || spec.enabled;
            double weight = spec == null ? defaultWeight : spec.weight;
            if (enabled && weight > 0.0D) {
                builder.add(level, weight);
                if (level > CLIENT_DISPLAY_MAX_LEVEL) {
                    aboveClientDisplay = true;
                }
            }
        }
        WeightedTable<Integer> table = builder.build();
        if (table.isEmpty()) {
            report.warning(basePath, "nessun livello estraibile (tutti disattivati, con peso 0 o fuori intervallo): "
                    + "la categoria armature verra' esclusa");
        }
        if (aboveClientDisplay) {
            report.warning(basePath, "livelli oltre " + CLIENT_DISPLAY_MAX_LEVEL + " funzionano, ma il client 1.8.9 li mostra come "
                    + "'enchantment.level.N': valuta armor.item.hide-enchants e una lore con %protection%/%unbreaking%");
        }
        return table;
    }

    private void loadCommandRewards(ConfigurationSection root) {
        WeightedTable.Builder<CommandReward> table = WeightedTable.builder();
        ConfigurationSection rewards = readSection(root, "command-rewards");
        if (rewards != null) {
            for (String id : rewards.getKeys(false)) {
                String rewardPath = path(rewards, id);
                Object value = rewards.get(id);
                if (!(value instanceof ConfigurationSection)) {
                    report.error(rewardPath, "deve essere una sezione con enabled, weight, display-name, message e commands");
                    continue;
                }
                ConfigurationSection section = (ConfigurationSection) value;
                warnUnknownKeys(section, REWARD_KEYS);

                boolean enabled = readBoolean(section, "enabled", true);
                double weight = readWeight(section, "weight", 1.0D);
                String displayName = readString(section, "display-name", id);
                String message = section.contains("message") ? readString(section, "message", "") : null;

                List<String> rawCommands = readStringList(section, "commands", Collections.<String>emptyList());
                List<String> commands = new ArrayList<String>();
                for (int i = 0; i < rawCommands.size(); i++) {
                    String command = rawCommands.get(i).trim();
                    if (command.startsWith("/")) {
                        command = command.substring(1).trim();
                    }
                    if (command.isEmpty()) {
                        report.error(rewardPath + ".commands[" + i + "]", "comando vuoto");
                    } else {
                        commands.add(command);
                    }
                }

                if (!enabled) {
                    continue;
                }
                if (commands.isEmpty() && rawCommands.isEmpty()) {
                    report.error(rewardPath + ".commands", "la ricompensa e' attiva ma non contiene comandi");
                    continue;
                }
                if (weight <= 0.0D) {
                    report.warning(rewardPath + ".weight", "peso 0: la ricompensa non verra' mai estratta");
                    continue;
                }
                for (String command : commands) {
                    if (command.toUpperCase(Locale.ROOT).contains(PLACEHOLDER_MARKER)) {
                        report.warning(rewardPath + ".commands", "contiene ancora il comando segnaposto '" + command
                                + "': sostituiscilo con il comando reale del tuo plugin");
                    }
                }
                table.add(new CommandReward(id, displayName, message, commands), weight);
            }
        }
        settings.commandRewards = table.build();
    }

    private void loadMessages(ConfigurationSection root) {
        Map<MessageKey, String> values = new EnumMap<MessageKey, String>(MessageKey.class);
        ConfigurationSection section = readSection(root, "messages");
        boolean rewardMessages = true;
        if (section != null) {
            Set<String> allowed = new HashSet<String>();
            allowed.add("reward-messages-enabled");
            for (MessageKey key : MessageKey.values()) {
                allowed.add(key.path());
            }
            warnUnknownKeys(section, allowed);
            rewardMessages = readBoolean(section, "reward-messages-enabled", true);
        }
        for (MessageKey key : MessageKey.values()) {
            values.put(key, section == null ? key.defaultValue() : readString(section, key.path(), key.defaultValue()));
        }
        settings.messages = new Messages(values, rewardMessages);
    }

    /** Esclude le categorie disattivate, con peso 0 o senza ricompense valide. */
    private void buildCategoryTable(Map<RewardCategory, EntrySpec> categories) {
        WeightedTable.Builder<RewardCategory> table = WeightedTable.builder();
        for (RewardCategory category : RewardCategory.values()) {
            EntrySpec spec = categories.get(category);
            settings.configuredCategoryWeights.put(category, spec.weight);
            String reason = null;
            if (!spec.enabled) {
                reason = "disattivata";
            } else if (spec.weight <= 0.0D) {
                reason = "peso 0";
            } else {
                String missing = missingRewards(category);
                if (missing != null) {
                    reason = missing;
                    report.warning("categories." + category.key(), "categoria attiva ma " + missing
                            + ": esclusa, le probabilita' vengono ricalcolate sulle categorie rimaste");
                }
            }
            if (reason == null) {
                table.add(category, spec.weight);
            } else {
                settings.excludedCategories.put(category, reason);
            }
        }
        settings.categories = table.build();
        if (settings.categories.isEmpty()) {
            report.warning("categories", "nessuna categoria di ricompensa valida: la pesca restera' quella vanilla");
        }
    }

    private String missingRewards(RewardCategory category) {
        switch (category) {
            case FISH:
                return settings.fishTable.isEmpty() ? "nessun pesce attivo con peso > 0 in fish.types" : null;
            case ARMOR:
                if (settings.pieceTable.isEmpty()) {
                    return "nessun pezzo attivo con peso > 0 in armor.pieces";
                }
                if (settings.protectionLevels.isEmpty()) {
                    return "nessun livello di protezione estraibile";
                }
                if (settings.unbreakingLevels.isEmpty()) {
                    return "nessun livello di indistruttibilita' estraibile";
                }
                return null;
            case COMMANDS:
                return settings.commandRewards.isEmpty() ? "nessuna ricompensa attiva con peso > 0 in command-rewards" : null;
            default:
                return "categoria non gestita";
        }
    }

    // ------------------------------------------------------------------ lettori tipizzati

    /** Voce con {@code enabled}/{@code weight}: accetta una sezione oppure direttamente un numero (peso). */
    private EntrySpec readEntry(ConfigurationSection parent, String key, double defaultWeight, Set<String> allowedKeys) {
        Object value = parent.get(key);
        String entryPath = path(parent, key);
        EntrySpec spec = EntrySpec.defaults(defaultWeight);
        if (value instanceof ConfigurationSection) {
            ConfigurationSection section = (ConfigurationSection) value;
            warnUnknownKeys(section, allowedKeys);
            spec.enabled = readBoolean(section, "enabled", true);
            spec.weight = readWeight(section, "weight", defaultWeight);
            spec.section = section;
        } else if (value instanceof Number || value instanceof String) {
            spec.weight = parseWeight(value, entryPath);
        } else {
            report.error(entryPath, "deve essere una sezione con 'enabled' e 'weight' oppure un numero (peso)");
            spec.enabled = false;
        }
        return spec;
    }

    private boolean readBoolean(ConfigurationSection section, String key, boolean defaultValue) {
        Object value = section.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.equalsIgnoreCase("true")) {
                return true;
            }
            if (text.equalsIgnoreCase("false")) {
                return false;
            }
        }
        report.error(path(section, key), "deve essere true oppure false (trovato: " + value + ")");
        return defaultValue;
    }

    private double readWeight(ConfigurationSection section, String key, double defaultValue) {
        Object value = section.get(key);
        if (value == null) {
            return defaultValue;
        }
        return parseWeight(value, path(section, key));
    }

    private double parseWeight(Object value, String path) {
        double weight;
        if (value instanceof Number) {
            weight = ((Number) value).doubleValue();
        } else if (value instanceof String) {
            try {
                weight = Double.parseDouble(((String) value).trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                report.error(path, "il peso deve essere un numero >= 0 (trovato: " + value + ")");
                return 0.0D;
            }
        } else {
            report.error(path, "il peso deve essere un numero >= 0 (trovato: " + value + ")");
            return 0.0D;
        }
        if (Double.isNaN(weight) || Double.isInfinite(weight) || weight < 0.0D) {
            report.error(path, "il peso deve essere un numero finito >= 0 (trovato: " + value + ")");
            return 0.0D;
        }
        return weight;
    }

    private int readLevel(ConfigurationSection section, String key, int defaultValue) {
        Object value = section.get(key);
        String levelPath = path(section, key);
        if (value == null) {
            return defaultValue;
        }
        Long parsed = null;
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && !Double.isInfinite(number)) {
                parsed = (long) number;
            }
        } else if (value instanceof String) {
            try {
                parsed = Long.parseLong(((String) value).trim());
            } catch (NumberFormatException ignored) {
                parsed = null;
            }
        }
        if (parsed == null || parsed < MIN_ENCHANT_LEVEL || parsed > MAX_ENCHANT_LEVEL) {
            report.error(levelPath, "deve essere un intero tra " + MIN_ENCHANT_LEVEL + " e " + MAX_ENCHANT_LEVEL
                    + " (trovato: " + value + ")");
            return defaultValue;
        }
        return parsed.intValue();
    }

    private static Integer parseLevelKey(String key) {
        try {
            int level = Integer.parseInt(key.trim());
            return level >= MIN_ENCHANT_LEVEL && level <= MAX_ENCHANT_LEVEL ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String readString(ConfigurationSection section, String key, String defaultValue) {
        Object value = section.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        report.error(path(section, key), "deve essere un testo (trovato: " + describe(value) + ")");
        return defaultValue;
    }

    private List<String> readStringList(ConfigurationSection section, String key, List<String> defaultValue) {
        Object value = section.get(key);
        String listPath = path(section, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String) {
            return Collections.singletonList((String) value);
        }
        if (!(value instanceof List)) {
            report.error(listPath, "deve essere una lista di testi (trovato: " + describe(value) + ")");
            return defaultValue;
        }
        List<?> raw = (List<?>) value;
        List<String> result = new ArrayList<String>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Object element = raw.get(i);
            if (element instanceof String || element instanceof Number || element instanceof Boolean) {
                result.add(String.valueOf(element));
            } else {
                report.error(listPath + "[" + i + "]", "deve essere un testo (trovato: " + describe(element) + ")");
            }
        }
        return result;
    }

    private ConfigurationSection readSection(ConfigurationSection parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof ConfigurationSection) {
            return (ConfigurationSection) value;
        }
        report.error(path(parent, key), "deve essere una sezione (trovato: " + describe(value) + ")");
        return null;
    }

    private void warnUnknownKeys(ConfigurationSection section, Set<String> allowed) {
        for (String key : section.getKeys(false)) {
            if (!allowed.contains(key)) {
                report.warning(path(section, key), "chiave sconosciuta: ignorata (controlla eventuali errori di battitura)");
            }
        }
    }

    private static String path(ConfigurationSection section, String key) {
        String current = section.getCurrentPath();
        return current == null || current.isEmpty() ? key : current + "." + key;
    }

    private static String describe(Object value) {
        if (value == null) {
            return "vuoto";
        }
        if (value instanceof ConfigurationSection) {
            return "sezione";
        }
        if (value instanceof List) {
            return "lista";
        }
        return String.valueOf(value);
    }

    private static Set<String> keys(String... keys) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(keys)));
    }

    // ------------------------------------------------------------------ tipi di supporto

    private static final class EntrySpec {
        boolean enabled;
        double weight;
        ConfigurationSection section;

        static EntrySpec defaults(double weight) {
            EntrySpec spec = new EntrySpec();
            spec.enabled = true;
            spec.weight = weight;
            return spec;
        }
    }

    public static final class Result {
        private final PluginSettings settings;
        private final ValidationReport report;

        Result(PluginSettings settings, ValidationReport report) {
            this.settings = settings;
            this.report = report;
        }

        /** {@code null} se ci sono errori. */
        public PluginSettings settings() {
            return settings;
        }

        public ValidationReport report() {
            return report;
        }
    }
}
