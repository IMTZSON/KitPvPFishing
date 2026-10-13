package it.kitpvpfishing.testutil;

import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.config.SettingsLoader;
import org.junit.Assert;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class TestConfigs {

    private TestConfigs() {
    }

    public static String yaml(String... lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(line).append('\n');
        }
        return builder.toString();
    }

    public static String bundledConfig() {
        try {
            return new String(Files.readAllBytes(Paths.get("src", "main", "resources", "config.yml")), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Carica un config che deve essere valido (fallisce il test mostrando gli errori altrimenti). */
    public static PluginSettings valid(String yaml) {
        SettingsLoader.Result result = SettingsLoader.loadFromString(yaml);
        Assert.assertTrue("Errori inattesi: " + result.report().errors(), result.report().errors().isEmpty());
        Assert.assertNotNull(result.settings());
        return result.settings();
    }

    public static String categories(boolean fish, boolean armor, boolean commands) {
        return yaml(
                "categories:",
                "  fish: {enabled: " + fish + ", weight: 80}",
                "  armor: {enabled: " + armor + ", weight: 15}",
                "  commands: {enabled: " + commands + ", weight: 5}");
    }

    public static final String COMMAND_REWARDS = yaml(
            "command-rewards:",
            "  vip_key:",
            "    enabled: true",
            "    weight: 1",
            "    display-name: '&6Key VIP'",
            "    message: '&7Hai vinto %reward% &7(%player%)'",
            "    commands:",
            "      - '/givekey %player% vip 1'",
            "      - 'eco give %player% 100'",
            "  disabled_key:",
            "    enabled: false",
            "    weight: 1000",
            "    commands: []");
}
