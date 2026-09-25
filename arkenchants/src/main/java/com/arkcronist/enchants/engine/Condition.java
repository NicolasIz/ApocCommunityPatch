package com.arkcronist.enchants.engine;

import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * One AdvancedEnchantments condition: {@code <expression> : <result>}.
 *
 * <p>Results: {@code %stop%} cancels when the expression is true; {@code %allow%} lets the enchant go on only
 * when it is true; {@code %force%} skips the chance roll when true; {@code %chance%+N} adds N to the chance
 * when true; {@code %continue%} does nothing. Expressions join comparisons with {@code &&} and {@code ||}
 * ({@code &&} binds tighter) and compare with =, ==, !=, &gt;, &lt;, &gt;=, &lt;=, contains, !contains.</p>
 */
public record Condition(String expression, Result result, double chanceBonus) {

    public enum Result { STOP, ALLOW, FORCE, CHANCE, CONTINUE }

    /** Outcome of checking all conditions of an enchant level. */
    public record Verdict(boolean stop, boolean force, double chanceBonus) {
        public static final Verdict GO = new Verdict(false, false, 0);
    }

    private static final String[] OPS = {"!contains", "contains", ">=", "<=", "!=", "==", "=", ">", "<"};

    public static Condition parse(String raw) {
        String s = raw.trim();
        int sep = s.lastIndexOf(':');
        if (sep < 0) {
            return new Condition(s, Result.ALLOW, 0);
        }
        String expr = s.substring(0, sep).trim();
        String res = s.substring(sep + 1).trim().toLowerCase(Locale.ROOT);
        if (res.startsWith("%stop%")) {
            return new Condition(expr, Result.STOP, 0);
        }
        if (res.startsWith("%allow%")) {
            return new Condition(expr, Result.ALLOW, 0);
        }
        if (res.startsWith("%force%")) {
            return new Condition(expr, Result.FORCE, 0);
        }
        if (res.startsWith("%chance%")) {
            double bonus = 0;
            try {
                bonus = Double.parseDouble(res.substring("%chance%".length()).replace("+", "").trim());
            } catch (NumberFormatException ignored) {
                // a malformed bonus just counts as nothing
            }
            return new Condition(expr, Result.CHANCE, bonus);
        }
        return new Condition(expr, Result.CONTINUE, 0);
    }

    /** Checks every condition in order; placeholders are resolved by {@code resolver}. */
    public static Verdict check(Iterable<Condition> conditions, UnaryOperator<String> resolver) {
        boolean force = false;
        double bonus = 0;
        for (Condition c : conditions) {
            boolean t = evaluate(resolver.apply(c.expression));
            switch (c.result) {
                case STOP -> {
                    if (t) {
                        return new Verdict(true, false, 0);
                    }
                }
                case ALLOW -> {
                    if (!t) {
                        return new Verdict(true, false, 0);
                    }
                }
                case FORCE -> force |= t;
                case CHANCE -> bonus += t ? c.chanceBonus : 0;
                case CONTINUE -> {
                }
            }
        }
        return new Verdict(false, force, bonus);
    }

    /** True/false of an expression whose placeholders are already filled in. */
    public static boolean evaluate(String expression) {
        for (String any : expression.split("\\|\\|")) {
            boolean all = true;
            for (String part : any.split("&&")) {
                if (!compare(part.trim())) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    static boolean compare(String part) {
        String lower = part.toLowerCase(Locale.ROOT);
        for (String op : OPS) {
            int i = op.chars().allMatch(Character::isLetter) || op.startsWith("!c")
                    ? indexOfWord(lower, op) : lower.indexOf(op);
            if (i < 0) {
                continue;
            }
            String a = part.substring(0, i).trim();
            String b = part.substring(i + op.length()).trim();
            return apply(op, a, b);
        }
        // a lone value: true / non-empty and not "false" / "0"
        String v = part.trim().toLowerCase(Locale.ROOT);
        return !v.isEmpty() && !v.equals("false") && !v.equals("0");
    }

    private static int indexOfWord(String s, String word) {
        int from = 0;
        while (true) {
            int i = s.indexOf(word, from);
            if (i < 0) {
                return -1;
            }
            boolean startOk = i == 0 || s.charAt(i - 1) == ' ';
            if (word.equals("contains") && i > 0 && s.charAt(i - 1) == '!') {
                from = i + 1;
                continue;
            }
            if (startOk) {
                return i;
            }
            from = i + 1;
        }
    }

    private static boolean apply(String op, String a, String b) {
        Double x = num(a);
        Double y = num(b);
        switch (op) {
            case "contains":
                return a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case "!contains":
                return !a.toLowerCase(Locale.ROOT).contains(b.toLowerCase(Locale.ROOT));
            case "=":
            case "==":
                return x != null && y != null ? x.doubleValue() == y.doubleValue() : a.equalsIgnoreCase(b);
            case "!=":
                return x != null && y != null ? x.doubleValue() != y.doubleValue() : !a.equalsIgnoreCase(b);
            default:
                if (x == null || y == null) {
                    return false;
                }
                return switch (op) {
                    case ">" -> x > y;
                    case "<" -> x < y;
                    case ">=" -> x >= y;
                    case "<=" -> x <= y;
                    default -> false;
                };
        }
    }

    private static Double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
