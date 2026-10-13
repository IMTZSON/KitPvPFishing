package it.kitpvpfishing.command;

import it.kitpvpfishing.config.ConfigManager;
import it.kitpvpfishing.config.PluginSettings;
import it.kitpvpfishing.testutil.Fakes;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static it.kitpvpfishing.testutil.TestConfigs.bundledConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AdminCommandTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file;
    private ConfigManager manager;
    private AdminCommand command;
    private final List<String> messages = new ArrayList<String>();
    private boolean admin;
    private CommandSender sender;

    @Before
    public void setUp() throws IOException {
        file = folder.newFile("config.yml");
        write(bundledConfig());
        manager = new ConfigManager(file, new Fakes.CapturingLogger().logger);
        manager.reload();
        command = new AdminCommand(manager);
        sender = Fakes.proxy(CommandSender.class, new Fakes.Behavior() {
            @Override
            public Object invoke(String method, Object[] args) {
                if (method.equals("hasPermission")) {
                    assertEquals(AdminCommand.PERMISSION, args[0]);
                    return admin;
                }
                if (method.equals("sendMessage") && args[0] instanceof String) {
                    messages.add(ChatColor.stripColor((String) args[0]));
                    return null;
                }
                return Fakes.UNHANDLED;
            }
        });
    }

    @Test
    public void requiresAdminPermission() {
        admin = false;
        PluginSettings before = manager.current();
        command.onCommand(sender, null, "kitpvpfishing", new String[]{"reload"});
        assertEquals(Collections.singletonList("[Pesca] Non hai il permesso per usare questo comando."), messages);
        assertSame(before, manager.current());
        assertTrue(command.onTabComplete(sender, null, "kitpvpfishing", new String[]{""}).isEmpty());
    }

    @Test
    public void reloadWithErrorsKeepsConfigurationAndShowsPaths() throws IOException {
        admin = true;
        PluginSettings before = manager.current();
        write(bundledConfig().replace("weight: 80", "weight: ottanta"));
        command.onCommand(sender, null, "kitpvpfishing", new String[]{"reload"});
        assertSame(before, manager.current());
        assertTrue(messages.get(0).contains("Configurazione non valida (1 errori)"));
        assertTrue(messages.get(1).contains("[categories.fish.weight]"));
    }

    @Test
    public void successfulReloadAppliesNewConfiguration() throws IOException {
        admin = true;
        PluginSettings before = manager.current();
        write(bundledConfig().replace("weight: 80", "weight: 40"));
        command.onCommand(sender, null, "kitpvpfishing", new String[]{"reload"});
        assertTrue(before != manager.current());
        assertTrue(messages.get(0).startsWith("[Pesca] Configurazione ricaricata."));
    }

    @Test
    public void chancesShowRecalculatedPercentages() {
        admin = true;
        command.onCommand(sender, null, "kitpvpfishing", new String[]{"chances"});
        String output = String.join("\n", messages);
        assertTrue(output, output.contains("Pesce (peso 80): 84,21%"));
        assertTrue(output, output.contains("Armatura (peso 15): 15,79%"));
        assertTrue(output, output.contains("Comandi (peso 5): esclusa"));
        assertTrue(output, output.contains("Elmo di diamante (peso 25): 25,00% / 3,95%"));
    }

    @Test
    public void usageAndTabCompletion() {
        admin = true;
        command.onCommand(sender, null, "kpfishing", new String[0]);
        assertEquals("[Pesca] Uso: /kpfishing reload oppure /kpfishing chances", messages.get(0));
        assertEquals(Arrays.asList("reload"), command.onTabComplete(sender, null, "kitpvpfishing", new String[]{"re"}));
    }

    private void write(String text) throws IOException {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
