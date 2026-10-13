package it.kitpvpfishing.reward;

import it.kitpvpfishing.config.PluginSettings;

import java.util.Random;

/**
 * Logica di estrazione, indipendente dal server:
 * <ol>
 *     <li>estrae la categoria tra quelle valide;</li>
 *     <li>estrae il pesce, il pezzo di armatura oppure la ricompensa a comandi;</li>
 *     <li>per l'armatura estrae Protezione e Indistruttibilita' con due estrazioni separate.</li>
 * </ol>
 */
public final class RewardRoller {

    private RewardRoller() {
    }

    /**
     * @return la ricompensa estratta, oppure {@code null} se non esiste alcuna categoria valida
     *         (in quel caso la pesca deve restare vanilla).
     */
    public static RolledReward roll(PluginSettings settings, Random random) {
        RewardCategory category = settings.categories().pick(random);
        if (category == null) {
            return null;
        }
        switch (category) {
            case FISH:
                return RolledReward.fish(settings.fishTable().pick(random));
            case ARMOR:
                ArmorPiece piece = settings.pieceTable().pick(random);
                // Due estrazioni indipendenti: il livello di Protezione non influenza Indistruttibilita'.
                int protection = settings.protectionLevels().pick(random);
                int unbreaking = settings.unbreakingLevels().pick(random);
                return RolledReward.armor(piece, protection, unbreaking);
            case COMMANDS:
                return RolledReward.command(settings.commandRewards().pick(random));
            default:
                throw new IllegalStateException("Categoria non gestita: " + category);
        }
    }
}
