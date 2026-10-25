package com.arkcronist.content.core.furniture;

import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * 3.2: the world seen from above, drawn as an old map on parchment - for the map board of the
 * medieval props, which shows the server's own spawn.
 *
 * <p>Each pixel is one column of the world, as a vanilla map draws it: the colour of its top block,
 * brighter where the ground climbs from the north and darker where it falls, water darker the deeper
 * it is. Over that the parchment: the colours toned towards it, a little grain, the edges browned,
 * a double border, a compass rose in the top right corner, and a red cross where the spawn is.
 * Columns nobody has explored are bare parchment.</p>
 */
public final class SpawnMap {

    /** The map board's picture, in pixels: twenty to eleven, as the board's canvas. */
    public static final int WIDTH = 320;
    public static final int HEIGHT = 176;

    private static final int PARCHMENT = 0xE4D2A4;
    private static final int INK = 0x4A3220;
    private static final int RED = 0xB0261E;

    private SpawnMap() {
    }

    /**
     * One column of the world, as the map sees it.
     *
     * @param rgb        the map colour of its top block, 0xRRGGBB
     * @param height     its top block's y
     * @param waterDepth how deep the water over its floor is; 0 for dry land
     */
    public record Column(int rgb, int height, int waterDepth) {
    }

    /**
     * The picture.
     *
     * @param columns every column, by row (north first) then across (west first); null where nothing
     *                was explored
     * @param markX   the spawn's pixel across, or -1 for no mark
     * @param markY   its pixel down
     */
    public static BufferedImage draw(@Nullable Column[][] columns, int markX, int markY) {
        int height = columns.length;
        int width = height == 0 ? 0 : columns[0].length;
        BufferedImage map = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Column column = columns[y][x];
                int colour;
                if (column == null || column.rgb() == 0) {
                    colour = PARCHMENT;
                } else {
                    double shade;
                    if (column.waterDepth() > 0) {
                        shade = column.waterDepth() <= 2 ? 1.0 : column.waterDepth() <= 5 ? 0.86 : 0.71;
                    } else {
                        Column north = y > 0 ? columns[y - 1][x] : null;
                        int ground = north == null || north.rgb() == 0 ? column.height() : north.height();
                        shade = column.height() > ground ? 1.0 : column.height() < ground ? 0.71 : 0.86;
                    }
                    colour = mix(scale(column.rgb(), shade), PARCHMENT, 0.28);
                }
                colour = shade(colour, 1 + (grain(x, y) - 0.5) * 0.08);
                // The edges browned, as old paper is.
                double edge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                if (edge < 14) {
                    colour = mix(colour, 0x8A6A3C, (14 - edge) / 14.0 * 0.45);
                }
                map.setRGB(x, y, 0xFF000000 | colour);
            }
        }
        border(map);
        compass(map, width - 22, 21);
        if (markX >= 0 && markY >= 0 && markX < width && markY < height) {
            cross(map, markX, markY);
        }
        return map;
    }

    /** A double line round the edge: dark ink outside, a thin one inside it. */
    private static void border(BufferedImage map) {
        int width = map.getWidth();
        int height = map.getHeight();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int edge = Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
                if (edge <= 1 || edge == 4) {
                    map.setRGB(x, y, 0xFF000000 | INK);
                }
            }
        }
    }

    /** An eight-pointed star in ink, its north point red: which way is up. */
    private static void compass(BufferedImage map, int cx, int cy) {
        int[][] long_ = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : long_) {
            for (int i = 0; i <= 12; i++) {
                int half = Math.max(0, (12 - i) / 5);
                for (int w = -half; w <= half; w++) {
                    int x = cx + d[0] * i + (d[1] != 0 ? w : 0);
                    int y = cy + d[1] * i + (d[0] != 0 ? w : 0);
                    set(map, x, y, d[1] == -1 ? RED : INK);
                }
            }
        }
        int[][] short_ = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int[] d : short_) {
            for (int i = 0; i <= 6; i++) {
                set(map, cx + d[0] * i, cy + d[1] * i, INK);
            }
        }
        for (int a = 0; a < 360; a += 4) {
            double r = Math.toRadians(a);
            set(map, cx + (int) Math.round(Math.cos(r) * 8), cy + (int) Math.round(Math.sin(r) * 8), INK);
        }
    }

    /** A red cross with a dark edge: here is the spawn. */
    private static void cross(BufferedImage map, int cx, int cy) {
        for (int pass = 0; pass < 2; pass++) {
            int colour = pass == 0 ? INK : RED;
            int thick = pass == 0 ? 1 : 0;
            for (int i = -4; i <= 4; i++) {
                for (int t = -thick; t <= thick; t++) {
                    set(map, cx + i, cy + i + t, colour);
                    set(map, cx + i, cy - i + t, colour);
                    set(map, cx + i + t, cy + i, colour);
                    set(map, cx + i + t, cy - i, colour);
                }
            }
        }
    }

    private static void set(BufferedImage map, int x, int y, int rgb) {
        if (x >= 0 && y >= 0 && x < map.getWidth() && y < map.getHeight()) {
            map.setRGB(x, y, 0xFF000000 | rgb);
        }
    }

    /** Paper's grain: a fixed pattern, so the same world draws the same picture. */
    static double grain(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535.0;
    }

    private static int scale(int rgb, double factor) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * factor);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * factor);
        int b = (int) Math.round((rgb & 0xFF) * factor);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int shade(int rgb, double factor) {
        return scale(rgb, factor);
    }

    static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = (int) Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(bl);
    }

    private static int clamp(int channel) {
        return Math.max(0, Math.min(255, channel));
    }
}
