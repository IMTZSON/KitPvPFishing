package it.kitpvpfishing;

import it.kitpvpfishing.command.AdminCommand;
import it.kitpvpfishing.config.ConfigManager;
import it.kitpvpfishing.listener.FishingListener;
import it.kitpvpfishing.reward.RewardDelivery;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Random;

/**
 * Punto di ingresso del plugin KitPvPFishing (Spigot/Bukkit 1.8.8, client 1.8.9).
 */
public final class KitPvPFishingPlugin extends JavaPlugin {

    private ConfigManager configManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new ConfigManager(new File(getDataFolder(), "config.yml"), getLogger());
        configManager.reload();

        RewardDelivery delivery = new RewardDelivery(getServer(), getLogger());
        getServer().getPluginManager().registerEvents(
                new FishingListener(configManager, delivery, getLogger(), new Random()), this);

        AdminCommand adminCommand = new AdminCommand(configManager);
        PluginCommand command = getCommand("kitpvpfishing");
        if (command == null) {
            getLogger().severe("Comando 'kitpvpfishing' assente da plugin.yml: il comando di reload non sara' disponibile.");
        } else {
            command.setExecutor(adminCommand);
            command.setTabCompleter(adminCommand);
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }
}
