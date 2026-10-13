package it.kitpvpfishing.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ricompensa eseguita tramite comandi da console (es. key di un plugin di casse).
 */
public final class CommandReward {

    private final String id;
    private final String displayName;
    /** Messaggio al giocatore; {@code null} = usa il messaggio predefinito, vuoto = nessun messaggio. */
    private final String message;
    private final List<String> commands;

    public CommandReward(String id, String displayName, String message, List<String> commands) {
        this.id = id;
        this.displayName = displayName;
        this.message = message;
        this.commands = Collections.unmodifiableList(new ArrayList<String>(commands));
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String message() {
        return message;
    }

    public List<String> commands() {
        return commands;
    }

    @Override
    public String toString() {
        return "CommandReward{" + id + ", comandi=" + commands.size() + "}";
    }
}
