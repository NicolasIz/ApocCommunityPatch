package com.arkcronist.content.core.block;

import java.util.Locale;

/**
 * 5.5: which way a block that turns as a log does - {@code block: axis: true} - lies: upright, or on
 * its side along x or z. Each way is a note block state of its own, kept in the same assignment file
 * as the block's, under the block's id with {@link #suffix()} - {@code demo:ruby_log#x}.
 */
public enum BlockAxis {
    Y(""), X("#x"), Z("#z");

    private final String suffix;

    BlockAxis(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    /** The assignment key of {@code id} lying this way. */
    public String key(String id) {
        return id + suffix;
    }

    /** The way an assignment key lies: {@code demo:log#x} is X, {@code demo:log} Y. */
    public static BlockAxis of(String key) {
        if (key.endsWith(X.suffix)) {
            return X;
        }
        return key.endsWith(Z.suffix) ? Z : Y;
    }

    /** The block's id in an assignment key. */
    public static String base(String key) {
        int hash = key.indexOf('#');
        return hash < 0 ? key : key.substring(0, hash);
    }

    /** As a vanilla log's {@code axis} property: x, y or z. */
    public String vanilla() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The turn of the model vanilla gives a log lying this way: {@code [x, y]} in degrees. */
    public int[] rotation() {
        return switch (this) {
            case Y -> new int[]{0, 0};
            case X -> new int[]{90, 90};
            case Z -> new int[]{90, 0};
        };
    }
}
