package it.kitpvpfishing.reward;

import it.kitpvpfishing.config.MessageKey;
import it.kitpvpfishing.config.Messages;
import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Consegna al giocatore la ricompensa gia' estratta: oggetti, comandi e messaggi.
 */
public final class RewardDelivery {

    private final Server server;
    private final Logger logger;

    public RewardDelivery(Server server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    public void deliver(Player player, PluginSettings settings, RolledReward reward) {
        Messages messages = settings.messages();
        switch (reward.category()) {
            case FISH:
                // Il pesce viaggia verso il giocatore come nella pesca vanilla (sostituisce il bottino originale).
                messages.send(player, MessageKey.REWARD_FISH,
                        "%item%", settings.fishName(reward.fish()),
                        "%player%", player.getName());
                break;
            case ARMOR:
                ItemStack armor = ArmorItemFactory.create(settings, reward, player.getName());
                boolean dropped = giveOrDrop(player, armor);
                messages.send(player, MessageKey.REWARD_ARMOR, ArmorItemFactory.placeholders(settings, reward, player.getName()));
                if (dropped) {
                    messages.send(player, MessageKey.INVENTORY_FULL);
                }
                break;
            case COMMANDS:
                CommandReward commandReward = reward.commandReward();
                runCommands(player, commandReward);
                String[] placeholders = {"%reward%", commandReward.displayName(), "%player%", player.getName()};
                if (commandReward.message() == null) {
                    messages.send(player, MessageKey.REWARD_COMMAND_DEFAULT, placeholders);
                } else if (messages.rewardMessagesEnabled()) {
                    messages.sendRaw(player, commandReward.message(), placeholders);
                }
                break;
            default:
                throw new IllegalStateException("Categoria non gestita: " + reward.category());
        }
    }

    /**
     * Inserisce l'oggetto nell'inventario; la parte che non entra viene lasciata a terra
     * nella posizione del giocatore. Non crea mai copie: viene droppato solo il resto
     * restituito da {@code addItem}.
     *
     * @return {@code true} se qualcosa e' stato lasciato a terra
     */
    public static boolean giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (leftovers == null || leftovers.isEmpty()) {
            return false;
        }
        Location location = player.getLocation();
        boolean dropped = false;
        for (ItemStack leftover : leftovers.values()) {
            if (leftover != null && leftover.getType() != Material.AIR && leftover.getAmount() > 0) {
                player.getWorld().dropItem(location, leftover);
                dropped = true;
            }
        }
        return dropped;
    }

    /**
     * Esegue tutti i comandi della ricompensa dalla console. Un comando che fallisce
     * non blocca i successivi e viene segnalato nel log.
     */
    private void runCommands(Player player, CommandReward reward) {
        for (String template : reward.commands()) {
            String command = Text.apply(template,
                    "%player%", player.getName(),
                    "%uuid%", player.getUniqueId().toString(),
                    "%world%", player.getWorld().getName());
            try {
                if (!server.dispatchCommand(server.getConsoleSender(), command)) {
                    logger.warning("Ricompensa '" + reward.id() + "': comando non riconosciuto dal server: /" + command
                            + " (controlla command-rewards." + reward.id() + ".commands)");
                }
            } catch (RuntimeException e) {
                logger.log(Level.SEVERE, "Ricompensa '" + reward.id() + "': errore durante l'esecuzione di /" + command, e);
            }
        }
    }
}
