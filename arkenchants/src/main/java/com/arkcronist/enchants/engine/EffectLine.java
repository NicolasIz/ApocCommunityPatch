package com.arkcronist.enchants.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One parsed effect line in AdvancedEnchantments syntax, e.g.
 * {@code POTION:SLOW:0:100 @Victim{r=3} <chance>25</chance> <condition>%pitch% <= -30 : %allow%</condition>}.
 *
 * <p>Arguments keep their raw text so random/math tags and placeholders can be filled in each time the effect
 * runs. A target given as a last ':'-argument ({@code GUARD:ZOMBIE:8:2:@Victim}) counts as the target.</p>
 */
public record EffectLine(String name, List<String> args, String target, Map<String, String> targetArgs,
                         double chance, List<Condition> conditions, String raw) {

    private static final Pattern CONDITION = Pattern.compile("<condition>(.*?)</condition>");
    private static final Pattern CHANCE = Pattern.compile("<chance>\\s*([\\d.]+)\\s*</chance>");
    private static final Pattern TARGET = Pattern.compile("(?:^|\\s|:)@([A-Za-z]+)(\\{[^}]*\\})?");

    public static EffectLine parse(String raw) {
        String s = raw.trim();
        List<Condition> conditions = new ArrayList<>();
        Matcher cm = CONDITION.matcher(s);
        while (cm.find()) {
            conditions.add(Condition.parse(cm.group(1)));
        }
        s = CONDITION.matcher(s).replaceAll("").trim();
        double chance = 100;
        Matcher ch = CHANCE.matcher(s);
        if (ch.find()) {
            chance = Double.parseDouble(ch.group(1));
            s = ch.replaceAll("").trim();
        }
        String target = null;
        Map<String, String> targetArgs = Collections.emptyMap();
        Matcher tm = TARGET.matcher(s);
        int cut = -1;
        while (tm.find()) {
            target = tm.group(1).toUpperCase(Locale.ROOT);
            targetArgs = parseArgs(tm.group(2));
            cut = tm.start();
        }
        if (cut >= 0) {
            // drop the target (and a trailing ':' when it was written as the last argument)
            String before = s.substring(0, cut);
            Matcher again = TARGET.matcher(s);
            String rest = "";
            while (again.find()) {
                rest = s.substring(again.end());
            }
            s = (before + " " + rest).trim();
            if (s.endsWith(":")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        // the name ends at the first ':' or space; everything after the name is ':'-separated arguments
        int colon = s.indexOf(':');
        int space = s.indexOf(' ');
        int end = colon < 0 ? (space < 0 ? s.length() : space) : (space < 0 ? colon : Math.min(colon, space));
        String name = s.substring(0, end).trim().toUpperCase(Locale.ROOT);
        List<String> args = new ArrayList<>();
        if (end < s.length() && s.charAt(end) == ':') {
            String rest = s.substring(end + 1);
            args.addAll(splitArgs(rest));
        }
        return new EffectLine(name, List.copyOf(args), target, targetArgs, chance, List.copyOf(conditions), raw);
    }

    /** Splits on ':' but not inside tags such as {@code <math>a:b</math>} (which never contain ':' in practice). */
    private static List<String> splitArgs(String rest) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ':' && depth == 0) {
                out.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0 || !out.isEmpty()) {
            out.add(cur.toString().trim());
        }
        return out;
    }

    static Map<String, String> parseArgs(String braces) {
        if (braces == null || braces.length() < 2) {
            return Collections.emptyMap();
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (String kv : braces.substring(1, braces.length() - 1).split("[,;]")) {
            int eq = kv.indexOf('=');
            if (eq > 0) {
                m.put(kv.substring(0, eq).trim().toLowerCase(Locale.ROOT), kv.substring(eq + 1).trim());
            }
        }
        return Collections.unmodifiableMap(m);
    }

    public String arg(int i, String def) {
        return i < args.size() && !args.get(i).isEmpty() ? args.get(i) : def;
    }
}
