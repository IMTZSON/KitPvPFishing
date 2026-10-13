package it.kitpvpfishing.config;

import it.kitpvpfishing.reward.RewardCategory;
import it.kitpvpfishing.testutil.Fakes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;

import static it.kitpvpfishing.testutil.TestConfigs.bundledConfig;
import static it.kitpvpfishing.testutil.TestConfigs.categories;
import static it.kitpvpfishing.testutil.TestConfigs.yaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConfigManagerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void reloadWithErrorsKeepsLastValidConfiguration() throws IOException {
        File file = folder.newFile("config.yml");
        Fakes.CapturingLogger log = new Fakes.CapturingLogger();
        ConfigManager manager = new ConfigManager(file, log.logger);

        write(file, bundledConfig());
        assertFalse(manager.reload().hasErrors());
        PluginSettings first = manager.current();
        assertTrue(first.categories().contains(RewardCategory.FISH));

        write(file, bundledConfig().replace("    protection:\n      min: 4\n      max: 10", "    protection:\n      min: 12\n      max: 10"));
        ValidationReport report = manager.reload();
        assertTrue(report.hasErrors());
        assertSame("La configurazione precedente deve restare attiva", first, manager.current());
        assertTrue(log.contains(Level.SEVERE, "[armor.enchantments.protection] min (12) e' maggiore di max (10)"));
        assertTrue(log.contains(Level.SEVERE, "resta attiva l'ultima configurazione valida"));

        write(file, "enabled: true\ncategories: [\n");
        assertTrue(manager.reload().hasErrors());
        assertSame(first, manager.current());

        write(file, bundledConfig().replace("enabled: true\n\n# Mondi", "enabled: false\n\n# Mondi"));
        assertFalse(manager.reload().hasErrors());
        assertFalse(manager.current().enabled());
    }

    @Test
    public void invalidConfigAtStartupLeavesFishingVanilla() throws IOException {
        File file = folder.newFile("config.yml");
        Fakes.CapturingLogger log = new Fakes.CapturingLogger();
        ConfigManager manager = new ConfigManager(file, log.logger);
        write(file, yaml("categories:", "  fish: {enabled: true, weight: -1}"));
        assertTrue(manager.reload().hasErrors());
        assertNull(manager.current());
        assertTrue(log.contains(Level.SEVERE, "[categories.fish.weight]"));
        assertTrue(log.contains(Level.SEVERE, "la pesca resta vanilla"));
    }

    @Test
    public void missingFileIsReportedWithoutCrashing() {
        Fakes.CapturingLogger log = new Fakes.CapturingLogger();
        ConfigManager manager = new ConfigManager(new File(folder.getRoot(), "inesistente.yml"), log.logger);
        assertTrue(manager.reload().hasErrors());
        assertNull(manager.current());
    }

    @Test
    public void fileIsAlwaysReadAsUtf8() throws IOException {
        File file = folder.newFile("config.yml");
        ConfigManager manager = new ConfigManager(file, new Fakes.CapturingLogger().logger);
        write(file, categories(true, false, false) + yaml("messages:", "  reward-fish: 'Perché è così: %item%'"));
        assertFalse(manager.reload().hasErrors());
        assertEquals("Perché è così: %item%", manager.current().messages().raw(MessageKey.REWARD_FISH));
        // Con BOM UTF-8 iniziale (tipico di alcuni editor Windows).
        Files.write(file.toPath(), ("﻿" + "enabled: true\n").getBytes(StandardCharsets.UTF_8));
        assertFalse(manager.reload().hasErrors());
    }

    private static void write(File file, String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
