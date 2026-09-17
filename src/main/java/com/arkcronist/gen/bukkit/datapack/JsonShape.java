package com.arkcronist.gen.bukkit.datapack;

/**
 * Whether a piece of text is JSON the game will accept.
 *
 * <p>Not a parser: nothing here builds a value, because nothing here needs one. The only question is
 * whether the file will survive being read, and the answer decides whether a datapack is about to
 * delete something that works.</p>
 *
 * <p>Minecraft reads data files strictly. A file that does not parse is not skipped in favour of the
 * one it replaces - the replacement simply does not exist, and if the file it replaced was one of
 * the game's own, that piece of the game is gone. A pack shipped with two malformed files and an
 * empty result id in four recipes took netherite armour off an entire server, and the only sign was
 * a stack trace in a startup log among two hundred other lines.</p>
 */
public final class JsonShape {

    private final String text;
    private int at;

    private JsonShape(String text) {
        this.text = text;
    }

    /** Whether this text is well-formed JSON. */
    public static boolean wellFormed(String text) {
        if (text == null) {
            return false;
        }
        JsonShape reader = new JsonShape(text);
        reader.skipSpace();
        if (!reader.value()) {
            return false;
        }
        reader.skipSpace();
        return reader.at == text.length();
    }

    private boolean value() {
        if (at >= text.length()) {
            return false;
        }
        char c = text.charAt(at);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true");
            case 'f' -> literal("false");
            case 'n' -> literal("null");
            default -> number();
        };
    }

    private boolean object() {
        at++;                       // past '{'
        skipSpace();
        if (peek() == '}') {
            at++;
            return true;
        }
        while (true) {
            skipSpace();
            if (peek() != '"' || !string()) {
                return false;
            }
            skipSpace();
            if (peek() != ':') {
                return false;
            }
            at++;
            skipSpace();
            if (!value()) {
                return false;
            }
            skipSpace();
            char c = peek();
            if (c == ',') {
                at++;
                continue;
            }
            if (c == '}') {
                at++;
                return true;
            }
            return false;
        }
    }

    private boolean array() {
        at++;                       // past '['
        skipSpace();
        if (peek() == ']') {
            at++;
            return true;
        }
        while (true) {
            skipSpace();
            if (!value()) {
                return false;
            }
            skipSpace();
            char c = peek();
            if (c == ',') {
                at++;
                continue;
            }
            if (c == ']') {
                at++;
                return true;
            }
            return false;
        }
    }

    private boolean string() {
        at++;                       // past the opening quote
        while (at < text.length()) {
            char c = text.charAt(at++);
            if (c == '"') {
                return true;
            }
            if (c == '\\') {
                if (at >= text.length()) {
                    return false;
                }
                char escape = text.charAt(at++);
                if (escape == 'u') {
                    if (at + 4 > text.length()) {
                        return false;
                    }
                    for (int i = 0; i < 4; i++) {
                        if (Character.digit(text.charAt(at++), 16) < 0) {
                            return false;
                        }
                    }
                } else if ("\"\\/bfnrt".indexOf(escape) < 0) {
                    return false;
                }
            } else if (c < 0x20) {
                return false;       // a raw control character, which strict readers reject
            }
        }
        return false;
    }

    private boolean number() {
        int start = at;
        if (peek() == '-') {
            at++;
        }
        if (!digits()) {
            return false;
        }
        if (peek() == '.') {
            at++;
            if (!digits()) {
                return false;
            }
        }
        char c = peek();
        if (c == 'e' || c == 'E') {
            at++;
            c = peek();
            if (c == '+' || c == '-') {
                at++;
            }
            if (!digits()) {
                return false;
            }
        }
        return at > start;
    }

    private boolean digits() {
        int start = at;
        while (at < text.length() && Character.isDigit(text.charAt(at))) {
            at++;
        }
        return at > start;
    }

    private boolean literal(String word) {
        if (text.startsWith(word, at)) {
            at += word.length();
            return true;
        }
        return false;
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void skipSpace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }
}
