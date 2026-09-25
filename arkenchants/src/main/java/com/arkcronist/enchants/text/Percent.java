package com.arkcronist.enchants.text;

import java.util.Locale;
import java.util.random.RandomGenerator;

/** Percentages with decimals (a book can have 0.1% success). */
public final class Percent {

    private Percent() {
    }

    /** 45 -> "45", 0.1 -> "0.1", 12.35 -> "12.35". */
    public static String fmt(double v) {
        double r = Math.round(v * 100) / 100.0;
        if (r == Math.rint(r)) {
            return String.valueOf((long) r);
        }
        String s = String.format(Locale.ROOT, "%.2f", r);
        return s.endsWith("0") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * A value between min and max with one decimal (two below 1%, so 0.1-5 can give 0.35). Never below min, so a
     * range starting at 0.1 never rolls 0.
     */
    public static double roll(double min, double max, RandomGenerator r) {
        if (max <= min) {
            return min;
        }
        double v = min + r.nextDouble() * (max - min);
        double step = v < 1 ? 100 : 10;
        v = Math.round(v * step) / step;
        return Math.max(min, Math.min(max, v));
    }

    /** True with the given chance in percent (0-100, decimals allowed). */
    public static boolean chance(double percent, RandomGenerator r) {
        return r.nextDouble() * 100 < percent;
    }

    public static double parse(String s, double def) {
        try {
            return Double.parseDouble(s.replace(',', '.').replace("%", "").trim());
        } catch (RuntimeException e) {
            return def;
        }
    }
}
