package it.kitpvpfishing.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Carica {@code config.yml} e mantiene l'ultima configurazione valida.
 * <p>
 * Se il file contiene errori, la configurazione corrente non viene sostituita:
 * al primo avvio questo significa che la pesca resta vanilla finche' il file non viene corretto.
 */
public final class ConfigManager {

    private final File file;
    private final Logger logger;
    private volatile PluginSettings current;

    public ConfigManager(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    /** Configurazione attiva, oppure {@code null} se non e' mai stata caricata una configurazione valida. */
    public PluginSettings current() {
        return current;
    }

    /**
     * Rilegge il file. Il file viene sempre letto in UTF-8, indipendentemente dalla codifica
     * di sistema del server (su Windows la 1.8 userebbe altrimenti la codifica di sistema).
     */
    public ValidationReport reload() {
        SettingsLoader.Result result;
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            result = SettingsLoader.loadFromString(text);
        } catch (IOException e) {
            ValidationReport report = new ValidationReport();
            report.error(file.getName(), "impossibile leggere il file: " + e.getMessage());
            log(report);
            logOutcome(false);
            return report;
        } catch (RuntimeException e) {
            // Non dovrebbe accadere: qualsiasi valore inatteso deve diventare un errore di validazione.
            ValidationReport report = new ValidationReport();
            report.error(file.getName(), "errore imprevisto durante la lettura: " + e);
            logger.log(Level.SEVERE, "Errore imprevisto durante il caricamento di " + file.getName(), e);
            logOutcome(false);
            return report;
        }

        ValidationReport report = result.report();
        log(report);
        if (result.settings() != null) {
            current = result.settings();
            logOutcome(true);
        } else {
            logOutcome(false);
        }
        return report;
    }

    private void log(ValidationReport report) {
        for (String warning : report.warnings()) {
            logger.warning("Config: " + warning);
        }
        for (String error : report.errors()) {
            logger.severe("Config non valido: " + error);
        }
    }

    private void logOutcome(boolean applied) {
        if (applied) {
            PluginSettings settings = current;
            logger.info("Configurazione caricata: plugin " + (settings.enabled() ? "attivo" : "disattivato")
                    + ", categorie estraibili: " + settings.categories().values()
                    + (settings.worlds().isEmpty() ? ", tutti i mondi" : ", mondi: " + settings.worlds()));
        } else if (current != null) {
            logger.severe("Il config contiene errori: resta attiva l'ultima configurazione valida. "
                    + "Correggi i percorsi indicati e usa /kitpvpfishing reload.");
        } else {
            logger.severe("Il config contiene errori e non esiste una configurazione valida precedente: "
                    + "la pesca resta vanilla finche' il file non viene corretto (poi /kitpvpfishing reload).");
        }
    }
}
