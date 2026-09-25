package com.arkcronist.enchants.text;

/** Tiny arithmetic evaluator for {@code <math>} tags: + - * / % ^, parentheses and min/max/abs/floor/ceil/round/sqrt. */
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
        int fstart = pos;
        while (pos < src.length() && Character.isLetter(src.charAt(pos))) {
            pos++;
        }
        if (pos > fstart) {
            String fn = src.substring(fstart, pos).toLowerCase(java.util.Locale.ROOT);
            if (pos >= src.length() || src.charAt(pos) != '(') {
                throw new IllegalArgumentException("'(' expected after " + fn);
            }
            pos++;
            java.util.List<Double> args = new java.util.ArrayList<>();
            args.add(sum());
            while (pos < src.length() && src.charAt(pos) == ',') {
                pos++;
                args.add(sum());
            }
            if (pos < src.length() && src.charAt(pos) == ')') {
                pos++;
            }
            double a0 = args.get(0);
            return switch (fn) {
                case "min" -> args.stream().mapToDouble(Double::doubleValue).min().orElse(0);
                case "max" -> args.stream().mapToDouble(Double::doubleValue).max().orElse(0);
                case "abs" -> Math.abs(a0);
                case "floor" -> Math.floor(a0);
                case "ceil" -> Math.ceil(a0);
                case "round" -> Math.round(a0);
                case "sqrt" -> Math.sqrt(Math.max(0, a0));
                default -> throw new IllegalArgumentException("Unknown function " + fn);
            };
        }
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
