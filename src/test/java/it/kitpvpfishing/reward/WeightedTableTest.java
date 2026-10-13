package it.kitpvpfishing.reward;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeightedTableTest {

    @Test
    public void emptyTableReturnsNull() {
        assertNull(WeightedTable.<String>empty().pick(new Random(1)));
        assertNull(WeightedTable.<String>builder().add("zero", 0).build().pick(new Random(1)));
    }

    @Test
    public void nonPositiveOrInvalidWeightsAreNeverPicked() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("zero", 0)
                .add("negativo", -3)
                .add("nan", Double.NaN)
                .add("infinito", Double.POSITIVE_INFINITY)
                .add("valido", 2)
                .build();
        assertEquals(1, table.size());
        Random random = new Random(42);
        for (int i = 0; i < 10000; i++) {
            assertEquals("valido", table.pick(random));
        }
    }

    @Test
    public void distributionFollowsRelativeWeights() {
        WeightedTable<String> table = WeightedTable.<String>builder()
                .add("fish", 80).add("armor", 15).add("commands", 5).build();
        Map<String, Integer> counts = count(table, 300000, 7L);
        assertClose(0.80, counts.get("fish") / 300000.0, 0.005);
        assertClose(0.15, counts.get("armor") / 300000.0, 0.005);
        assertClose(0.05, counts.get("commands") / 300000.0, 0.005);
    }

    @Test
    public void weightsDoNotNeedToSumTo100() {
        WeightedTable<String> a = WeightedTable.<String>builder().add("x", 80).add("y", 15).add("z", 5).build();
        WeightedTable<String> b = WeightedTable.<String>builder().add("x", 16).add("y", 3).add("z", 1).build();
        WeightedTable<String> c = WeightedTable.<String>builder().add("x", 0.8).add("y", 0.15).add("z", 0.05).build();
        for (String key : new String[]{"x", "y", "z"}) {
            assertClose(a.chance(key), b.chance(key), 1e-9);
            assertClose(a.chance(key), c.chance(key), 1e-9);
        }
        assertClose(0.8, a.chance("x"), 1e-9);
    }

    @Test
    public void extremeRandomValuesStayInsideTable() {
        WeightedTable<String> table = WeightedTable.<String>builder().add("a", 1).add("b", 1).build();
        Random almostOne = new Random() {
            @Override
            public double nextDouble() {
                return Math.nextAfter(1.0, 0.0);
            }
        };
        Random zero = new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
        assertEquals("b", table.pick(almostOne));
        assertEquals("a", table.pick(zero));
    }

    static <T> Map<T, Integer> count(WeightedTable<T> table, int draws, long seed) {
        Random random = new Random(seed);
        Map<T, Integer> counts = new HashMap<T, Integer>();
        for (int i = 0; i < draws; i++) {
            T value = table.pick(random);
            Integer current = counts.get(value);
            counts.put(value, current == null ? 1 : current + 1);
        }
        return counts;
    }

    static void assertClose(double expected, double actual, double tolerance) {
        assertTrue("atteso " + expected + " ottenuto " + actual, Math.abs(expected - actual) <= tolerance);
    }
}
