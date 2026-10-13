package it.kitpvpfishing.listener;

import it.kitpvpfishing.config.ConfigManager;
import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.reward.RewardDelivery;
import it.kitpvpfishing.reward.RewardRoller;
import it.kitpvpfishing.reward.RolledReward;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestisce le catture. Lavora in due fasi sullo stesso evento:
 * <ol>
 *     <li><b>HIGHEST</b> (ignora gli eventi gia' cancellati): estrae la ricompensa e modifica il bottino
 *     originale (sostituito da un pesce, oppure rimosso per armature e comandi);</li>
 *     <li><b>MONITOR</b>: se nessun plugin ha cancellato l'evento nel frattempo, consegna la ricompensa.</li>
 * </ol>
 * Solo lo stato {@code CAUGHT_FISH} produce ricompense: lancio, recupero a vuoto, entita' agganciate,
 * amo a terra e tentativi falliti vengono ignorati.
 */
public final class FishingListener implements Listener {

    private final ConfigManager configManager;
    private final RewardDelivery delivery;
    private final Logger logger;
    private final Random random;
    /** Ricompense estratte in attesa della fase MONITOR (le chiavi sono confrontate per identita'). */
    private final Map<PlayerFishEvent, Pending> pending = new WeakHashMap<PlayerFishEvent, Pending>();

    private PluginSettings warnedNoCategories;
    private boolean warnedUnexpectedCatch;

    public FishingListener(ConfigManager configManager, RewardDelivery delivery, Logger logger, Random random) {
        this.configManager = configManager;
        this.delivery = delivery;
        this.logger = logger;
        this.random = random;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCatch(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        PluginSettings settings = configManager.current();
        if (settings == null || !settings.enabled()) {
            return;
        }
        Player player = event.getPlayer();
        if (!settings.isWorldAllowed(player.getWorld().getName())) {
            return;
        }

        Entity caught = event.getCaught();
        if (!(caught instanceof Item)) {
            // Senza l'entita' del bottino non si puo' garantire l'assenza di doppie ricompense.
            if (!warnedUnexpectedCatch) {
                warnedUnexpectedCatch = true;
                logger.warning("Cattura senza oggetto pescato (" + (caught == null ? "null" : caught.getType())
                        + "): pesca lasciata vanilla. Avviso mostrato una sola volta.");
            }
            return;
        }

        RolledReward reward = RewardRoller.roll(settings, random);
        if (reward == null) {
            if (warnedNoCategories != settings) {
                warnedNoCategories = settings;
                logger.warning("Nessuna categoria di ricompensa valida nel config: la pesca resta vanilla. "
                        + "Controlla 'categories' e le ricompense, poi usa /kitpvpfishing reload.");
            }
            return;
        }

        Item caughtItem = (Item) caught;
        switch (reward.category()) {
            case FISH:
                // Sostituisce il bottino originale con un solo pesce: nessun oggetto aggiuntivo.
                caughtItem.setItemStack(reward.fish().createItem());
                break;
            case ARMOR:
            case COMMANDS:
                // Rimuove il bottino originale: l'entita' non e' ancora nel mondo e non verra' generata.
                caughtItem.remove();
                break;
            default:
                return;
        }
        pending.put(event, new Pending(settings, reward));
        if (settings.debug()) {
            logger.info("[debug] " + player.getName() + " ha pescato: " + reward);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCatchResult(PlayerFishEvent event) {
        Pending entry = pending.remove(event);
        if (entry == null) {
            return;
        }
        if (event.isCancelled()) {
            // Un altro plugin ha annullato la cattura dopo l'estrazione: nessun bottino, nessuna ricompensa.
            if (entry.settings.debug()) {
                logger.info("[debug] Cattura di " + event.getPlayer().getName()
                        + " annullata da un altro plugin: ricompensa " + entry.reward + " non consegnata.");
            }
            return;
        }
        try {
            delivery.deliver(event.getPlayer(), entry.settings, entry.reward);
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "Errore durante la consegna della ricompensa " + entry.reward
                    + " a " + event.getPlayer().getName(), e);
        }
    }

    private static final class Pending {
        private final PluginSettings settings;
        private final RolledReward reward;

        Pending(PluginSettings settings, RolledReward reward) {
            this.settings = settings;
            this.reward = reward;
        }
    }
}
