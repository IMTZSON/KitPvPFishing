package it.kitpvpfishing.command;

import it.kitpvpfishing.config.ConfigManager;
import it.kitpvpfishing.config.MessageKey;
import it.kitpvpfishing.config.Messages;
import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.config.ValidationReport;
import it.kitpvpfishing.reward.ArmorPiece;
import it.kitpvpfishing.reward.CommandReward;
import it.kitpvpfishing.reward.FishType;
import it.kitpvpfishing.reward.RewardCategory;
import it.kitpvpfishing.reward.WeightedTable;
import it.kitpvpfishing.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@code /kitpvpfishing reload} e {@code /kitpvpfishing chances}, riservati a {@code kitpvpfishing.admin}.
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "kitpvpfishing.admin";
    private static final int MAX_ERRORS_IN_CHAT = 8;
    private static final List<String> SUBCOMMANDS = Collections.unmodifiableList(Arrays.asList("reload", "chances"));

    private final ConfigManager configManager;

    public AdminCommand(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = messages();
        if (!sender.hasPermission(PERMISSION)) {
            messages.send(sender, MessageKey.NO_PERMISSION);
            return true;
        }
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            reload(sender);
        } else if (sub.equals("chances") || sub.equals("probabilita")) {
            chances(sender);
        } else {
            messages.send(sender, MessageKey.USAGE, "%label%", label);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !sender.hasPermission(PERMISSION)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (String sub : SUBCOMMANDS) {
            if (sub.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                result.add(sub);
            }
        }
        return result;
    }

    private void reload(CommandSender sender) {
        ValidationReport report = configManager.reload();
        // Dopo un reload riuscito i messaggi da usare sono quelli nuovi.
        Messages messages = messages();
        if (report.hasErrors()) {
            messages.send(sender, MessageKey.RELOAD_FAILED, "%errors%", String.valueOf(report.errorCount()));
            List<String> errors = report.errors();
            for (int i = 0; i < errors.size() && i < MAX_ERRORS_IN_CHAT; i++) {
                sender.sendMessage(Text.color("&c- &7" + errors.get(i)));
            }
            if (errors.size() > MAX_ERRORS_IN_CHAT) {
                sender.sendMessage(Text.color("&7... altri " + (errors.size() - MAX_ERRORS_IN_CHAT) + " errori in console."));
            }
        } else {
            messages.send(sender, MessageKey.RELOAD_SUCCESS, "%warnings%", String.valueOf(report.warnings().size()));
            for (String warning : report.warnings()) {
                sender.sendMessage(Text.color("&e- &7" + warning));
            }
        }
    }

    private void chances(CommandSender sender) {
        PluginSettings settings = configManager.current();
        if (settings == null) {
            sender.sendMessage(Text.color("&cNessuna configurazione valida caricata: la pesca e' vanilla."));
            return;
        }
        sender.sendMessage(Text.color("&b&lKitPvPFishing &7- probabilita' per cattura riuscita"));
        if (!settings.enabled()) {
            sender.sendMessage(Text.color("&cPlugin disattivato dal config (enabled: false): pesca vanilla."));
        }
        sender.sendMessage(Text.color("&7Mondi: &f" + (settings.worlds().isEmpty() ? "tutti" : settings.worlds().toString())));

        WeightedTable<RewardCategory> categories = settings.categories();
        sender.sendMessage(Text.color("&eCategorie:"));
        for (RewardCategory category : RewardCategory.values()) {
            String weight = Text.weight(settings.configuredCategoryWeights().get(category));
            String excluded = settings.excludedCategories().get(category);
            if (excluded == null) {
                sender.sendMessage(Text.color("&7- &f" + category.displayName() + " &7(peso " + weight + "): &a"
                        + Text.percent(categories.chance(category))));
            } else {
                sender.sendMessage(Text.color("&7- &f" + category.displayName() + " &7(peso " + weight + "): &cesclusa &7- " + excluded));
            }
        }
        if (categories.isEmpty()) {
            sender.sendMessage(Text.color("&cNessuna categoria valida: la pesca resta vanilla."));
            return;
        }

        double fishChance = categories.chance(RewardCategory.FISH);
        if (fishChance > 0.0D) {
            sender.sendMessage(Text.color("&ePesci &7(nella categoria / per cattura):"));
            for (WeightedTable.Entry<FishType> entry : settings.fishTable().entries()) {
                line(sender, settings.fishName(entry.value()), settings.fishTable(), entry, fishChance);
            }
        }

        double armorChance = categories.chance(RewardCategory.ARMOR);
        if (armorChance > 0.0D) {
            sender.sendMessage(Text.color("&ePezzi di armatura &7(nella categoria / per cattura):"));
            for (WeightedTable.Entry<ArmorPiece> entry : settings.pieceTable().entries()) {
                line(sender, settings.pieceName(entry.value()), settings.pieceTable(), entry, armorChance);
            }
            levels(sender, "Protezione", settings.protectionLevels());
            levels(sender, "Indistruttibilità", settings.unbreakingLevels());
        }

        double commandChance = categories.chance(RewardCategory.COMMANDS);
        if (commandChance > 0.0D) {
            sender.sendMessage(Text.color("&eRicompense a comandi &7(nella categoria / per cattura):"));
            for (WeightedTable.Entry<CommandReward> entry : settings.commandRewards().entries()) {
                line(sender, entry.value().id() + " &7(" + entry.value().displayName() + "&7)",
                        settings.commandRewards(), entry, commandChance);
            }
        }
    }

    private static <T> void line(CommandSender sender, String name, WeightedTable<T> table,
                                 WeightedTable.Entry<T> entry, double categoryChance) {
        double inCategory = entry.weight() / table.total();
        sender.sendMessage(Text.color("&7- &f" + name + " &7(peso " + Text.weight(entry.weight()) + "): &a"
                + Text.percent(inCategory) + " &7/ &a" + Text.percent(inCategory * categoryChance)));
    }

    private static void levels(CommandSender sender, String enchantment, WeightedTable<Integer> table) {
        StringBuilder builder = new StringBuilder("&e" + enchantment + " &7(estrazione indipendente): ");
        boolean first = true;
        for (WeightedTable.Entry<Integer> entry : table.entries()) {
            if (!first) {
                builder.append("&7, ");
            }
            first = false;
            builder.append("&f").append(entry.value()).append(" &a").append(Text.percent(entry.weight() / table.total()));
        }
        sender.sendMessage(Text.color(builder.toString()));
    }

    private Messages messages() {
        PluginSettings settings = configManager.current();
        return settings == null ? Messages.defaults() : settings.messages();
    }
}
