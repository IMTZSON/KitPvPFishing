package it.kitpvpfishing.reward;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Tabella di estrazione a pesi relativi, immutabile.
 * <p>
 * I pesi non devono sommare a 100: la probabilita' di una voce e' {@code peso / somma dei pesi}.
 * Le voci con peso non positivo o non finito vengono scartate in fase di costruzione,
 * quindi non possono mai essere estratte.
 */
public final class WeightedTable<T> {

    private static final WeightedTable<Object> EMPTY = new WeightedTable<Object>(Collections.<Entry<Object>>emptyList());

    private final List<Entry<T>> entries;
    private final double total;

    private WeightedTable(List<Entry<T>> entries) {
        this.entries = Collections.unmodifiableList(entries);
        double sum = 0.0D;
        for (Entry<T> entry : entries) {
            sum += entry.weight;
        }
        this.total = sum;
    }

    @SuppressWarnings("unchecked")
    public static <T> WeightedTable<T> empty() {
        return (WeightedTable<T>) EMPTY;
    }

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }

    /**
     * Estrae una voce. Restituisce {@code null} solo se la tabella e' vuota.
     */
    public T pick(Random random) {
        if (entries.isEmpty()) {
            return null;
        }
        double roll = random.nextDouble() * total;
        for (Entry<T> entry : entries) {
            roll -= entry.weight;
            if (roll < 0.0D) {
                return entry.value;
            }
        }
        // Protezione contro gli errori di arrotondamento in virgola mobile.
        return entries.get(entries.size() - 1).value;
    }

    /** Probabilita' (0..1) di estrarre la voce indicata. */
    public double chance(T value) {
        if (total <= 0.0D) {
            return 0.0D;
        }
        double weight = 0.0D;
        for (Entry<T> entry : entries) {
            if (entry.value == null ? value == null : entry.value.equals(value)) {
                weight += entry.weight;
            }
        }
        return weight / total;
    }

    public boolean contains(T value) {
        return chance(value) > 0.0D;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public double total() {
        return total;
    }

    public List<Entry<T>> entries() {
        return entries;
    }

    public List<T> values() {
        List<T> values = new ArrayList<T>(entries.size());
        for (Entry<T> entry : entries) {
            values.add(entry.value);
        }
        return values;
    }

    public static final class Entry<T> {
        private final T value;
        private final double weight;

        Entry(T value, double weight) {
            this.value = value;
            this.weight = weight;
        }

        public T value() {
            return value;
        }

        public double weight() {
            return weight;
        }
    }

    public static final class Builder<T> {
        private final List<Entry<T>> entries = new ArrayList<Entry<T>>();

        private Builder() {
        }

        /** Aggiunge una voce; i pesi {@code <= 0}, NaN o infiniti vengono ignorati. */
        public Builder<T> add(T value, double weight) {
            if (weight > 0.0D && !Double.isInfinite(weight) && !Double.isNaN(weight)) {
                entries.add(new Entry<T>(value, weight));
            }
            return this;
        }

        public WeightedTable<T> build() {
            return new WeightedTable<T>(new ArrayList<Entry<T>>(entries));
        }
    }
}
