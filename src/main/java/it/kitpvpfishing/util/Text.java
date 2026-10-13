package it.kitpvpfishing.util;

import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility per colori, placeholder e formattazione dei numeri.
 */
public final class Text {

    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private Text() {
    }

    /** Traduce i codici colore con {@code &}. Restituisce stringa vuota per {@code null}. */
    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Sostituisce i placeholder passati come coppie chiave/valore,
     * es. {@code apply("Ciao %player%", "%player%", "Steve")}.
     */
    public static String apply(String template, String... pairs) {
        if (template == null) {
            return "";
        }
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("I placeholder devono essere coppie chiave/valore");
        }
        String result = template;
        for (int i = 0; i < pairs.length; i += 2) {
            String value = pairs[i + 1] == null ? "" : pairs[i + 1];
            result = result.replace(pairs[i], value);
        }
        return result;
    }

    public static List<String> apply(List<String> templates, String... pairs) {
        List<String> result = new ArrayList<String>(templates.size());
        for (String template : templates) {
            result.add(apply(template, pairs));
        }
        return result;
    }

    /** Numero romano per 1..3999; per valori fuori intervallo restituisce il numero arabo. */
    public static String roman(int number) {
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }
        StringBuilder builder = new StringBuilder();
        int remaining = number;
        for (int i = 0; i < ROMAN_VALUES.length; i++) {
            while (remaining >= ROMAN_VALUES[i]) {
                remaining -= ROMAN_VALUES[i];
                builder.append(ROMAN_SYMBOLS[i]);
            }
        }
        return builder.toString();
    }

    /** Formatta una frazione (0..1) come percentuale italiana, es. {@code 12,50%}. */
    public static String percent(double fraction) {
        return String.format(Locale.ITALIAN, "%.2f%%", fraction * 100.0D);
    }

    /** Formatta un peso senza decimali inutili, es. {@code 80} oppure {@code 0,5}. */
    public static String weight(double weight) {
        if (weight == Math.rint(weight) && !Double.isInfinite(weight)) {
            return String.valueOf((long) weight);
        }
        return String.format(Locale.ITALIAN, "%.3f", weight).replaceAll("0+$", "");
    }
}
