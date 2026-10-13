package it.kitpvpfishing.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Errori e avvisi raccolti durante la validazione, ciascuno con il percorso esatto nel config.
 * <ul>
 *     <li><b>Errore</b>: la configurazione viene rifiutata e resta attiva l'ultima valida.</li>
 *     <li><b>Avviso</b>: la configurazione viene accettata, ma qualcosa merita attenzione
 *     (es. una categoria esclusa perche' priva di ricompense valide).</li>
 * </ul>
 */
public final class ValidationReport {

    private final List<String> errors = new ArrayList<String>();
    private final List<String> warnings = new ArrayList<String>();

    public void error(String path, String message) {
        errors.add("[" + path + "] " + message);
    }

    public void warning(String path, String message) {
        warnings.add("[" + path + "] " + message);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public int errorCount() {
        return errors.size();
    }

    public List<String> errors() {
        return Collections.unmodifiableList(errors);
    }

    public List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }
}
