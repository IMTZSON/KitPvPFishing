package it.kitpvpfishing.reward;

/**
 * Risultato di una singola estrazione: esattamente una categoria e i relativi dettagli.
 */
public final class RolledReward {

    private final RewardCategory category;
    private final FishType fish;
    private final ArmorPiece piece;
    private final int protectionLevel;
    private final int unbreakingLevel;
    private final CommandReward commandReward;

    private RolledReward(RewardCategory category, FishType fish, ArmorPiece piece,
                         int protectionLevel, int unbreakingLevel, CommandReward commandReward) {
        this.category = category;
        this.fish = fish;
        this.piece = piece;
        this.protectionLevel = protectionLevel;
        this.unbreakingLevel = unbreakingLevel;
        this.commandReward = commandReward;
    }

    public static RolledReward fish(FishType fish) {
        return new RolledReward(RewardCategory.FISH, fish, null, 0, 0, null);
    }

    public static RolledReward armor(ArmorPiece piece, int protectionLevel, int unbreakingLevel) {
        return new RolledReward(RewardCategory.ARMOR, null, piece, protectionLevel, unbreakingLevel, null);
    }

    public static RolledReward command(CommandReward reward) {
        return new RolledReward(RewardCategory.COMMANDS, null, null, 0, 0, reward);
    }

    public RewardCategory category() {
        return category;
    }

    public FishType fish() {
        return fish;
    }

    public ArmorPiece piece() {
        return piece;
    }

    public int protectionLevel() {
        return protectionLevel;
    }

    public int unbreakingLevel() {
        return unbreakingLevel;
    }

    public CommandReward commandReward() {
        return commandReward;
    }

    @Override
    public String toString() {
        switch (category) {
            case FISH:
                return "Pesce[" + fish.key() + "]";
            case ARMOR:
                return "Armatura[" + piece.key() + ", protezione=" + protectionLevel + ", indistruttibilita=" + unbreakingLevel + "]";
            default:
                return "Comandi[" + commandReward.id() + "]";
        }
    }
}
