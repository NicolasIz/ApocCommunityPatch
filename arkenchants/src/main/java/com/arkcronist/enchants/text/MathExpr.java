package com.arkcronist.enchants.text;

/** Tiny arithmetic evaluator for AdvancedEnchantments' {@code <math>} tags: + - * / ^ and parentheses. */
public final class MathExpr {

    private final String src;
    private int pos;

    private MathExpr(String src) {
        this.src = src;
    }

    public static double eval(String expression) {
        MathExpr p = new MathExpr(expression.replace(" ", ""));
        double v = p.sum();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Unexpected '" + p.src.charAt(p.pos) + "' in " + expression);
        }
        return v;
    }

    private double sum() {
        double v = product();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '+') {
                pos++;
                v += product();
            } else if (c == '-') {
                pos++;
                v -= product();
            } else {
                break;
            }
        }
        return v;
    }

    private double product() {
        double v = power();
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '*') {
                pos++;
                v *= power();
            } else if (c == '/') {
                pos++;
                double d = power();
                v = d == 0 ? 0 : v / d;
            } else if (c == '%') {
                pos++;
                double d = power();
                v = d == 0 ? 0 : v % d;
            } else {
                break;
            }
        }
        return v;
    }

    private double power() {
        double v = unary();
        if (pos < src.length() && src.charAt(pos) == '^') {
            pos++;
            v = Math.pow(v, power());
        }
        return v;
    }

    private double unary() {
        if (pos < src.length() && src.charAt(pos) == '-') {
            pos++;
            return -unary();
        }
        if (pos < src.length() && src.charAt(pos) == '+') {
            pos++;
            return unary();
        }
        return atom();
    }

    private double atom() {
        if (pos < src.length() && src.charAt(pos) == '(') {
            pos++;
            double v = sum();
            if (pos < src.length() && src.charAt(pos) == ')') {
                pos++;
            }
            return v;
        }
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("Number expected at " + pos + " in " + src);
        }
        return Double.parseDouble(src.substring(start, pos));
    }
}
