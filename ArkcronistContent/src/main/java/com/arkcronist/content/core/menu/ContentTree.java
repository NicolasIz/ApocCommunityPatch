package com.arkcronist.content.core.menu;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The content folders as a tree, for the browser: {@code contents/dragones_epicos/...} becomes one
 * folder of the menu, {@code contents/arkespadas/...} another, each with the items its files define
 * and its own sub-folders.
 *
 * <p>A folder that holds no items of its own and exactly one sub-folder is not worth a click: it is
 * merged with that sub-folder, keeping the outer name. An imported pack's
 * {@code ginko_fantasy_shop/imported/ginko_fantasy_shop/configs/} is so one folder,
 * {@code ginko_fantasy_shop}.</p>
 *
 * <p>Plain data, built off nothing but ids and paths, so it can be tested without a server.</p>
 */
public final class ContentTree {

    /**
     * One folder.
     *
     * @param name    as shown: the folder's own name, or the outer one of a merged chain; empty for
     *                the root
     * @param path    where it is under {@code contents/}, {@code /}-separated, the innermost folder of
     *                a merged chain; empty for the root. Stable across rebuilds, which is how the menu
     *                finds its place again
     * @param folders sub-folders, by name
     * @param items   the ids of the items defined directly in it, sorted
     * @param total   items here and in every sub-folder
     */
    public record Node(String name, String path, List<Node> folders, List<String> items, int total) {

        public Node {
            folders = List.copyOf(folders);
            items = List.copyOf(items);
        }

        /** The folder at {@code path} at or below this one, or null when there is none any more. */
        public @Nullable Node find(String path) {
            if (path.equals(this.path)) {
                return this;
            }
            for (Node folder : folders) {
                Node found = folder.find(path);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
    }

    private ContentTree() {
    }

    /**
     * @param folderOfItem each item id and the folder its file is in, relative to {@code contents/},
     *                     {@code /}-separated; empty for a file directly in {@code contents/}
     */
    public static Node build(Map<String, String> folderOfItem) {
        Builder root = new Builder("", "");
        for (Map.Entry<String, String> entry : folderOfItem.entrySet()) {
            Builder at = root;
            String folder = entry.getValue().replace('\\', '/');
            StringBuilder path = new StringBuilder();
            for (String segment : folder.split("/")) {
                if (segment.isEmpty()) {
                    continue;
                }
                if (!path.isEmpty()) {
                    path.append('/');
                }
                path.append(segment);
                at = at.child(segment, path.toString());
            }
            at.items.add(entry.getKey());
        }
        return root.build(true);
    }

    private static final class Builder {

        final String name;
        final String path;
        final Map<String, Builder> children = new TreeMap<>();
        final List<String> items = new ArrayList<>();

        Builder(String name, String path) {
            this.name = name;
            this.path = path;
        }

        Builder child(String segment, String path) {
            return children.computeIfAbsent(segment, ignored -> new Builder(segment, path));
        }

        Node build(boolean root) {
            Builder inner = this;
            // A folder with only a folder in it is that folder, under the outer name.
            while (!root && inner.items.isEmpty() && inner.children.size() == 1) {
                inner = inner.children.values().iterator().next();
            }
            List<Node> folders = new ArrayList<>();
            int total = inner.items.size();
            for (Builder child : inner.children.values()) {
                Node node = child.build(false);
                folders.add(node);
                total += node.total();
            }
            folders.sort(Comparator.comparing(Node::name));
            List<String> sorted = new ArrayList<>(inner.items);
            sorted.sort(null);
            return new Node(name, inner.path, folders, sorted, total);
        }
    }
}
