package com.arkcronist.enchants.text;

/** Roman numerals for enchant levels, the way vanilla shows them. */
public final class Roman {

    private static final int[] VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private Roman() {
    }

    public static String of(int n) {
        if (n <= 0) {
            return String.valueOf(n);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < VALUES.length; i++) {
            while (n >= VALUES[i]) {
                out.append(SYMBOLS[i]);
                n -= VALUES[i];
            }
        }
        return out.toString();
    }

    /** Parses I..MMMCMXCIX; returns -1 when the text is not a roman numeral. */
    public static int parse(String s) {
        if (s == null || s.isEmpty()) {
            return -1;
        }
        int total = 0;
        int i = 0;
        for (int k = 0; k < VALUES.length && i < s.length(); k++) {
            while (s.startsWith(SYMBOLS[k], i)) {
                total += VALUES[k];
                i += SYMBOLS[k].length();
            }
        }
        return i == s.length() ? total : -1;
    }
}
