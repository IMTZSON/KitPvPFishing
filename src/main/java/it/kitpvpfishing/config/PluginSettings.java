package it.kitpvpfishing.config;

import it.kitpvpfishing.reward.ArmorPiece;
import it.kitpvpfishing.reward.CommandReward;
import it.kitpvpfishing.reward.FishType;
import it.kitpvpfishing.reward.RewardCategory;
import it.kitpvpfishing.reward.WeightedTable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Istantanea immutabile di una configurazione valida.
 * Viene creata solo da {@link SettingsLoader} e sostituita in blocco al reload.
 */
public final class PluginSettings {

    boolean enabled;
    boolean debug;
    /** Nomi dei mondi in minuscolo; vuoto = tutti i mondi. */
    Set<String> worlds = Collections.emptySet();

    WeightedTable<RewardCategory> categories = WeightedTable.empty();
    /** Peso configurato di ogni categoria (anche se esclusa), per il comando chances. */
    Map<RewardCategory, Double> configuredCategoryWeights = new EnumMap<RewardCategory, Double>(RewardCategory.class);
    /** Motivo di esclusione delle categorie non estraibili. */
    Map<RewardCategory, String> excludedCategories = new EnumMap<RewardCategory, String>(RewardCategory.class);

    WeightedTable<FishType> fishTable = WeightedTable.empty();
    Map<FishType, String> fishNames = new EnumMap<FishType, String>(FishType.class);

    WeightedTable<ArmorPiece> pieceTable = WeightedTable.empty();
    Map<ArmorPiece, String> pieceNames = new EnumMap<ArmorPiece, String>(ArmorPiece.class);
    WeightedTable<Integer> protectionLevels = WeightedTable.empty();
    WeightedTable<Integer> unbreakingLevels = WeightedTable.empty();
    String armorName = "";
    List<String> armorLore = Collections.emptyList();
    boolean hideEnchants;

    WeightedTable<CommandReward> commandRewards = WeightedTable.empty();

    Messages messages = Messages.defaults();

    PluginSettings() {
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean debug() {
        return debug;
    }

    public Set<String> worlds() {
        return worlds;
    }

    /** Lista vuota = tutti i mondi consentiti. Il confronto ignora maiuscole/minuscole. */
    public boolean isWorldAllowed(String worldName) {
        return worlds.isEmpty() || (worldName != null && worlds.contains(worldName.toLowerCase(java.util.Locale.ROOT)));
    }

    public WeightedTable<RewardCategory> categories() {
        return categories;
    }

    public Map<RewardCategory, Double> configuredCategoryWeights() {
        return Collections.unmodifiableMap(configuredCategoryWeights);
    }

    public Map<RewardCategory, String> excludedCategories() {
        return Collections.unmodifiableMap(excludedCategories);
    }

    public WeightedTable<FishType> fishTable() {
        return fishTable;
    }

    public String fishName(FishType type) {
        String name = fishNames.get(type);
        return name == null ? type.defaultName() : name;
    }

    public WeightedTable<ArmorPiece> pieceTable() {
        return pieceTable;
    }

    public String pieceName(ArmorPiece piece) {
        String name = pieceNames.get(piece);
        return name == null ? piece.defaultName() : name;
    }

    public WeightedTable<Integer> protectionLevels() {
        return protectionLevels;
    }

    public WeightedTable<Integer> unbreakingLevels() {
        return unbreakingLevels;
    }

    public String armorName() {
        return armorName;
    }

    public List<String> armorLore() {
        return armorLore;
    }

    public boolean hideEnchants() {
        return hideEnchants;
    }

    public WeightedTable<CommandReward> commandRewards() {
        return commandRewards;
    }

    public Messages messages() {
        return messages;
    }
}
