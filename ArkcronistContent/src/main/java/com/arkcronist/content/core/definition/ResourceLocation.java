package com.arkcronist.content.core.definition;

import java.util.regex.Pattern;

/**
 * A namespaced path as the client resolves it: {@code namespace:path}.
 *
 * <p>Validated here, once, with the client's own character rules. That matters beyond getting a
 * model to load: every path in a content file ends up joined onto a folder on disk while the pack
 * is compiled, and a path allowed to carry {@code ..} would let a content file copy anything the
 * server can read into a zip that is then served to anyone who asks for it.</p>
 */
public record ResourceLocation(String namespace, String path) {

    public static final String MINECRAFT = "minecraft";

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    public ResourceLocation {
        if (!isValidNamespace(namespace)) {
            throw new IllegalArgumentException("invalid namespace '" + namespace
                    + "' (allowed: a-z 0-9 _ . -)");
        }
        if (!isValidPath(path)) {
            throw new IllegalArgumentException("invalid path '" + path
                    + "' (allowed: a-z 0-9 _ . - and / between segments)");
        }
    }

    /**
     * Reads {@code namespace:path}, or a bare {@code path} in {@code defaultNamespace}.
     *
     * <p>Note the default is the caller's to choose. Inside a model file a bare path means
     * {@code minecraft:}, because that is what the client assumes; in a content file it means the
     * file's own namespace, because that is what someone writing one expects.</p>
     *
     * @throws IllegalArgumentException when either half breaks the client's rules
     */
    public static ResourceLocation parse(String raw, String defaultNamespace) {
        String text = raw.trim();
        int colon = text.indexOf(':');
        if (colon < 0) {
            return new ResourceLocation(defaultNamespace, text);
        }
        return new ResourceLocation(text.substring(0, colon), text.substring(colon + 1));
    }

    public static boolean isValidNamespace(String namespace) {
        return namespace != null && NAMESPACE.matcher(namespace).matches();
    }

    /** Client rules, plus no {@code .} or {@code ..} segment: these paths become file paths. */
    public static boolean isValidPath(String path) {
        if (path == null || !PATH.matcher(path).matches()) {
            return false;
        }
        for (String segment : path.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    /** Where this resource lives inside the pack, e.g. {@code assets/demo/models/item/ruby.json}. */
    public String assetPath(String folder, String extension) {
        return "assets/" + namespace + "/" + folder + "/" + path + extension;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
