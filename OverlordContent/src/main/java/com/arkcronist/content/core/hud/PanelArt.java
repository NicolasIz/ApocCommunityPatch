package com.arkcronist.content.core.hud;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * 3.1.1: a bar drawn as PrimeHUD draws its status bars, at their size - a frame 12 pixels tall with
 * a light outline, cut corners, a dark see-through inside and a small icon at its left end, and the
 * fill that goes over it, in the bar's colour, with the icon's shape left out - so a bar of this
 * plugin's sits among them as one more. All drawn here, from the bar's colour and icon.
 *
 * <p>The fill is two pixels narrower and shorter than the frame and goes a pixel right of and below
 * it, as PrimeHUD's own do.</p>
 */
public final class PanelArt {

    public static final int HEIGHT = BetterHudPanel.FRAME_HEIGHT;
    /** The inside of a frame: black, 60% opaque. */
    private static final int INSIDE = 0x99000000;
    private static final int WHITE = 0xFFFFFFFF;
    /** The box the icon sits in, in the frame: from (3, 3), 7 across and 6 down. */
    private static final int ICON_X = 3;
    private static final int ICON_Y = 3;
    public static final int ICON_WIDTH = 7;
    public static final int ICON_HEIGHT = 6;

    private PanelArt() {
    }

    /** The frame: outline, inside, icon. */
    public static BufferedImage frame(int width, int color, BufferedImage icon) {
        BufferedImage frame = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        int outline = 0xFF000000 | outline(color);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                switch (frameAt(x, y, width)) {
                    case 1 -> frame.setRGB(x, y, outline);
                    case 2 -> frame.setRGB(x, y, INSIDE);
                    default -> {
                    }
                }
            }
        }
        BufferedImage small = fitted(icon);
        int left = ICON_X + (ICON_WIDTH - small.getWidth()) / 2;
        int top = ICON_Y + (ICON_HEIGHT - small.getHeight()) / 2;
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                int pixel = small.getRGB(x, y);
                if ((pixel >>> 24) > 0) {
                    frame.setRGB(left + x, top + y, pixel | 0xFF000000);
                }
            }
        }
        return frame;
    }

    /**
     * The fill: the bar's colour inside the frame's outline, where the icon is not - two pixels
     * narrower and shorter than the frame, drawn a pixel right of and below it, as PrimeHUD's are.
     */
    public static BufferedImage fill(int width, int color, BufferedImage icon) {
        int inner = width - 2;
        BufferedImage fill = new BufferedImage(inner, HEIGHT - 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage shape = frame(width, color, icon);
        int solid = 0xFF000000 | (color & 0xFFFFFF);
        for (int y = 0; y < HEIGHT - 2; y++) {
            for (int x = 0; x < inner; x++) {
                if (fillAt(x, y, inner) && shape.getRGB(x + 1, y + 1) == INSIDE) {
                    fill.setRGB(x, y, solid);
                }
            }
        }
        return fill;
    }

    /** The blink of a bar running low: the outline and the icon's edge, white. */
    public static BufferedImage shine(int width, BufferedImage icon) {
        BufferedImage shine = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                if (frameAt(x, y, width) == 1) {
                    shine.setRGB(x, y, WHITE);
                }
            }
        }
        BufferedImage small = fitted(icon);
        int left = ICON_X + (ICON_WIDTH - small.getWidth()) / 2;
        int top = ICON_Y + (ICON_HEIGHT - small.getHeight()) / 2;
        for (int y = 0; y < small.getHeight(); y++) {
            for (int x = 0; x < small.getWidth(); x++) {
                if (opaque(small, x, y) && (!opaque(small, x - 1, y) || !opaque(small, x + 1, y)
                        || !opaque(small, x, y - 1) || !opaque(small, x, y + 1))) {
                    shine.setRGB(left + x, top + y, WHITE);
                }
            }
        }
        return shine;
    }

    /**
     * The other half of a blink: the outline, all but invisible (5%) - BetterHud takes no empty
     * picture, and both halves must cover the same pixels. PrimeHUD's blank is the same.
     */
    public static BufferedImage blank(int width) {
        BufferedImage blank = new BufferedImage(width, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                if (frameAt(x, y, width) == 1) {
                    blank.setRGB(x, y, 0x0DFFFFFF);
                }
            }
        }
        return blank;
    }

    /**
     * 0 outside, 1 outline, 2 inside: the frame's shape, PrimeHUD's - its corners cut by three pixels
     * along the top and bottom edges and two up the sides.
     */
    static int frameAt(int x, int y, int width) {
        int r = Math.min(y, HEIGHT - 1 - y);
        int c = Math.min(x, width - 1 - x);
        return switch (r) {
            case 0 -> c >= 3 ? 1 : 0;
            case 1 -> c == 0 ? 0 : c <= 3 ? 1 : 2;
            case 2 -> c == 0 ? 0 : c == 1 ? 1 : 2;
            case 3 -> c <= 1 ? 1 : 2;
            default -> c == 0 ? 1 : 2;
        };
    }

    /** The fill's shape, ten pixels tall and {@code width} across: its corners cut to sit inside the frame's. */
    static boolean fillAt(int x, int y, int width) {
        int r = Math.min(y, HEIGHT - 3 - y);
        int c = Math.min(x, width - 1 - x);
        return switch (r) {
            case 0 -> c >= 3;
            case 1, 2 -> c >= 1;
            default -> true;
        };
    }

    /**
     * The outline's colour: the bar's, each channel raised by as much as PrimeHUD's own are - its red
     * #cd3232 has the edge #ff8383, its blue #1765a8 the edge #60aced.
     */
    public static int outline(int color) {
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return (lighten(red) << 16) | (lighten(green) << 8) | lighten(blue);
    }

    private static int lighten(int channel) {
        return Math.min(255, channel + 72);
    }

    /**
     * A colour for a bar that names none: its icon's, the average of its opaque pixels, made strong
     * enough to read as a bar.
     */
    public static int colour(BufferedImage icon) {
        long red = 0;
        long green = 0;
        long blue = 0;
        long count = 0;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                int pixel = icon.getRGB(x, y);
                if ((pixel >>> 24) >= 128) {
                    red += (pixel >> 16) & 0xFF;
                    green += (pixel >> 8) & 0xFF;
                    blue += pixel & 0xFF;
                    count++;
                }
            }
        }
        if (count == 0) {
            return 0x8A8A8A;
        }
        float[] hsb = Color.RGBtoHSB((int) (red / count), (int) (green / count), (int) (blue / count), null);
        return Color.HSBtoRGB(hsb[0], Math.max(0.55f, hsb[1]), 0.78f) & 0xFFFFFF;
    }

    /** An icon brought into the frame's icon box: cut to what it draws, and made smaller if it does not fit, pixels kept sharp. */
    static BufferedImage fitted(BufferedImage icon) {
        int minX = icon.getWidth();
        int minY = icon.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int x = 0; x < icon.getWidth(); x++) {
                if (opaque(icon, x, y)) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        }
        BufferedImage cut = icon.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
        double scale = Math.min(1, Math.min((double) ICON_WIDTH / cut.getWidth(), (double) ICON_HEIGHT / cut.getHeight()));
        int width = Math.max(1, (int) Math.round(cut.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(cut.getHeight() * scale));
        BufferedImage fitted = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = fitted.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(cut, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return fitted;
    }

    private static boolean opaque(BufferedImage image, int x, int y) {
        return x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight() && (image.getRGB(x, y) >>> 24) >= 128;
    }
}
