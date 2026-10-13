package it.kitpvpfishing.config;

import it.kitpvpfishing.util.Text;
import org.bukkit.command.CommandSender;

import java.util.EnumMap;
import java.util.Map;

/**
 * Messaggi caricati dal config (testo grezzo con codici {@code &}).
 */
public final class Messages {

    private final Map<MessageKey, String> values;
    private final boolean rewardMessagesEnabled;

    Messages(Map<MessageKey, String> values, boolean rewardMessagesEnabled) {
        this.values = new EnumMap<MessageKey, String>(values);
        this.rewardMessagesEnabled = rewardMessagesEnabled;
    }

    public static Messages defaults() {
        Map<MessageKey, String> values = new EnumMap<MessageKey, String>(MessageKey.class);
        for (MessageKey key : MessageKey.values()) {
            values.put(key, key.defaultValue());
        }
        return new Messages(values, true);
    }

    public String raw(MessageKey key) {
        String value = values.get(key);
        return value == null ? key.defaultValue() : value;
    }

    public boolean rewardMessagesEnabled() {
        return rewardMessagesEnabled;
    }

    /**
     * Invia un messaggio configurato con prefisso e placeholder.
     * Non invia nulla se il testo e' vuoto o se e' un messaggio di ricompensa disattivato.
     */
    public void send(CommandSender target, MessageKey key, String... placeholders) {
        if (key.isRewardMessage() && !rewardMessagesEnabled) {
            return;
        }
        sendRaw(target, raw(key), placeholders);
    }

    /** Invia un testo arbitrario (es. il messaggio di una ricompensa a comandi) con prefisso. */
    public void sendRaw(CommandSender target, String text, String... placeholders) {
        if (text == null || text.isEmpty()) {
            return;
        }
        target.sendMessage(Text.color(raw(MessageKey.PREFIX) + Text.apply(text, placeholders)));
    }
}
