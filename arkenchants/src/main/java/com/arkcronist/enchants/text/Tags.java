package com.arkcronist.enchants.text;

import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The inline tags AdvancedEnchantments allows inside effects:
 * {@code <random number>1-4</random number>}, {@code <random word>a,b</random word>} and {@code <math>..</math>}.
 */
public final class Tags {

    private static final Pattern RANDOM_NUMBER = Pattern.compile("<random number>\\s*(-?\\d+)\\s*-\\s*(-?\\d+)\\s*</random number>");
    private static final Pattern RANDOM_WORD = Pattern.compile("<random word>(.*?)</random word>");
    private static final Pattern MATH = Pattern.compile("<math>(.*?)</math>");

    private Tags() {
    }

    /** Resolves random numbers and words first, then math (which may use the numbers). */
    public static String resolve(String text, RandomGenerator random) {
        if (text.indexOf('<') < 0) {
            return text;
        }
        String out = replace(RANDOM_NUMBER, text, m -> {
            int a = Integer.parseInt(m.group(1));
            int b = Integer.parseInt(m.group(2));
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            return String.valueOf(lo + random.nextInt(hi - lo + 1));
        });
        out = replace(RANDOM_WORD, out, m -> {
            String[] words = m.group(1).split(",");
            return words[random.nextInt(words.length)].trim();
        });
        out = replace(MATH, out, m -> {
            try {
                return number(MathExpr.eval(m.group(1)));
            } catch (IllegalArgumentException e) {
                return "0";
            }
        });
        return out;
    }

    /** Whole numbers print without ".0" so they can go back into integer arguments. */
    public static String number(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) {
            return String.valueOf((long) v);
        }
        return String.valueOf(Math.round(v * 1000.0) / 1000.0);
    }

    private static String replace(Pattern p, String text, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
