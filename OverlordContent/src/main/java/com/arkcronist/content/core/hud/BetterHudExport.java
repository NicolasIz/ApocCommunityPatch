package com.arkcronist.content.core.hud;

import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 3.1: HUD bars drawn by BetterHud, as HUDs of its own, instead of on this plugin's action bar - so
 * a server whose HUD is BetterHud's (PrimeHUD and the like) has the thirst, mana and shield bars
 * drawn by the same hand as its hearts.
 *
 * <p>3.1.1: where BetterHud already draws status bars - PrimeHUD's health and hunger bars, found by
 * {@link BetterHudPanel} - each bar is drawn as one more of them ({@link PanelArt}), in rows under
 * them, so the screen has one HUD and not two. Otherwise, or for a bar given a place of its own, as
 * follows.</p>
 *
 * <p>For each bar: two images, the bar full and the bar empty, made from its own icons laid out as
 * the action bar lays them out; BetterHud's {@code listener} image shows as much of the full one as
 * the bar's value is of its max - by halves when the bar has half icons - read through PlaceholderAPI
 * ({@code %arkcontent_hud_<id>_value%} and {@code _max}, as numbers: {@code (number)papi:...}), with
 * the empty one beneath. The bar keeps
 * its place: an anchored bar sits where the action bar put it, against the hotbar, from the middle
 * of the bottom of the screen.</p>
 *
 * <p>Everything goes under BetterHud's folder in files of this plugin's: {@code assets/arkcontent/},
 * and {@code arkcontent.yml} in its {@code images/}, {@code layouts/} and {@code huds/}, each HUD
 * {@code default: true} so every player has it. Names start {@code arkcontent_}, so they never meet
 * BetterHud's own or a pack's.</p>
 */
public final class BetterHudExport {

    /** The name of every file, folder and BetterHud object written. */
    public static final String NAME = "arkcontent";
    /** Where the files go, under BetterHud's folder. */
    public static final List<String> FILES = List.of("images/" + NAME + ".yml", "layouts/" + NAME + ".yml",
            "huds/" + NAME + ".yml");
    public static final String ASSETS = "assets/" + NAME + "/";
    /** The middle of the bottom of the screen, in BetterHud's percentages: where the action bar's bars are measured from. */
    public static final HudDefinition.Spot BOTTOM_MIDDLE = new HudDefinition.Spot(50, 100);
    /**
     * How far up BetterHud moves every HUD while its {@code disable-legacy-offset} is false (as
     * PrimeHUD's config.yml has it): the hotbar's height, {@code PixelLocation._hotBarHeight} in
     * BetterHud 2.0.0, (0, -54). A bar placed by this plugin is put back down by as much.
     */
    public static final int LEGACY_OFFSET = 54;

    private static final String HEADER = "# Written by OverlordContent from its HUDs (contents/*/huds.yml): change\n"
            + "# them there, or 'betterhud:' on one of them - this file is written again on each reload.\n";

    private BetterHudExport() {
    }

    /**
     * A bar to hand over: its layout, the name its placeholders know it by (its bare id, or
     * {@code namespace:id} when two namespaces share it), and its icons as images - with, 3.1.1, the
     * small one its {@code betterhud: icon} names for a bar drawn as BetterHud's status bars.
     */
    public record Bar(HudLayout hud, String placeholder, BufferedImage full, @Nullable BufferedImage half,
                      @Nullable BufferedImage empty, @Nullable BufferedImage small) {

        public Bar(HudLayout hud, String placeholder, BufferedImage full, @Nullable BufferedImage half,
                   @Nullable BufferedImage empty) {
            this(hud, placeholder, full, half, empty, null);
        }
    }

    /** Where a bar is drawn on BetterHud's screen. */
    public record Place(HudDefinition.Spot gui, HudDefinition.Spot pixel) {
    }

    /** BetterHud's name for a bar: {@code arkcontent_<namespace>_<id>}. */
    public static String name(HudDefinition hud) {
        return NAME + "_" + hud.namespace() + "_" + hud.id();
    }

    /**
     * Every file to write under BetterHud's folder, by path; none for no bars - the files there are
     * then to be removed.
     */
    public static Map<String, byte[]> files(List<Bar> bars) throws IOException {
        return files(bars, false, null);
    }

    /**
     * As {@link #files(List)}, for a BetterHud whose {@code disable-legacy-offset} is
     * {@code legacyOffset ? false : true}, and whose own status bars are {@code panel} (null for none):
     * a bar without a place of its own is then drawn as one more of them, in a row under them.
     */
    public static Map<String, byte[]> files(List<Bar> bars, boolean legacyOffset, @Nullable BetterHudPanel.Panel panel)
            throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (bars.isEmpty()) {
            return files;
        }
        StringBuilder images = new StringBuilder(HEADER);
        StringBuilder layouts = new StringBuilder(HEADER);
        StringBuilder huds = new StringBuilder(HEADER);
        Map<Bar, int[]> slots = panel == null ? Map.of() : slots(bars, panel);
        boolean blank = false;
        for (Bar bar : bars) {
            int[] slot = slots.get(bar);
            if (slot != null) {
                blank |= status(bar, panel, slot, files, images, layouts, huds);
            } else {
                icons(bar, legacyOffset, files, images, layouts, huds);
            }
        }
        if (blank) {
            files.put(ASSETS + "blank.png", png(PanelArt.blank(panel.width())));
        }
        files.put(FILES.get(0), images.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put(FILES.get(1), layouts.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        files.put(FILES.get(2), huds.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return files;
    }

    /**
     * 3.1.1: where each bar without a place of its own goes in the panel, {x, y}: a bar on the food
     * side (its anchor's) in the right column, the hunger bar's, any other in the left one, the
     * health bar's; down each column by anchor row, then id, one row each.
     */
    static Map<Bar, int[]> slots(List<Bar> bars, BetterHudPanel.Panel panel) {
        Map<Bar, int[]> slots = new LinkedHashMap<>();
        java.util.Comparator<Bar> order = java.util.Comparator
                .comparingInt((Bar bar) -> bar.hud().definition().anchor() == null ? 0 : bar.hud().definition().anchor().row())
                .thenComparing(bar -> bar.hud().definition().fullId());
        for (boolean right : new boolean[]{false, true}) {
            List<Bar> column = bars.stream()
                    .filter(bar -> bar.hud().definition().betterHud() == null || !bar.hud().definition().betterHud().placed())
                    .filter(bar -> right == (bar.hud().definition().anchor() != null && bar.hud().definition().anchor().right()))
                    .sorted(order).toList();
            for (int i = 0; i < column.size(); i++) {
                slots.put(column.get(i), new int[]{right ? panel.right() : panel.left(), panel.row(i)});
            }
        }
        return slots;
    }

    /**
     * 3.1.1: a bar as one of BetterHud's status bars: its frame, its fill shown as far as its value
     * goes, and - when it warns - a blink while it runs low; hidden while empty when it says so.
     *
     * @return whether it blinks, and needs the blank half of a blink
     */
    private static boolean status(Bar bar, BetterHudPanel.Panel panel, int[] slot, Map<String, byte[]> files,
                                  StringBuilder images, StringBuilder layouts, StringBuilder huds) throws IOException {
        HudDefinition hud = bar.hud().definition();
        HudDefinition.BetterHud own = hud.betterHud();
        String name = name(hud);
        String file = hud.namespace() + "_" + hud.id();
        BufferedImage icon = bar.small() != null ? bar.small() : bar.full();
        int color = own != null && own.color() != null ? own.color() : PanelArt.colour(bar.full());
        int width = panel.width();
        files.put(ASSETS + file + "_frame.png", png(PanelArt.frame(width, color, icon)));
        files.put(ASSETS + file + "_fill.png", png(PanelArt.fill(width, color, icon)));
        boolean warns = own != null && own.warnBelow() > 0;
        boolean hides = own != null && own.hideEmpty();
        String placeholder = "(number)papi:" + NAME + "_hud_" + bar.placeholder();
        images.append('\n').append(name).append("_frame:\n")
                .append("  type: single\n")
                .append("  file: ").append(quote(NAME + "/" + file + "_frame.png")).append('\n');
        images.append('\n').append(name).append(":\n")
                .append("  type: listener\n")
                .append("  file: ").append(quote(NAME + "/" + file + "_fill.png")).append('\n')
                .append("  split: ").append(Math.max(1, (width - 2) / 2)).append('\n')
                .append("  split-type: left\n")
                .append("  setting:\n")
                .append("    listener:\n")
                .append("      class: placeholder\n")
                .append("      value: ").append(quote(placeholder + "_value")).append('\n')
                .append("      max: ").append(quote(placeholder + "_max")).append('\n');
        if (warns) {
            files.put(ASSETS + file + "_shine.png", png(PanelArt.shine(width, icon)));
            images.append('\n').append(name).append("_shine:\n")
                    .append("  type: sequence\n")
                    .append("  files:\n")
                    .append("    - ").append(quote(NAME + "/" + file + "_shine.png:10")).append('\n')
                    .append("    - ").append(quote(NAME + "/blank.png:10")).append('\n');
        }
        String shown = hides ? condition(placeholder + "_value", "0", ">") : null;
        String low = warns ? condition(placeholder + "_percent", String.valueOf(own.warnBelow()), "<=") : null;
        layouts.append('\n').append(name).append(":\n").append("  images:\n");
        image(layouts, 1, name + "_frame", slot, shown == null ? List.of() : List.of(shown));
        // The fill a pixel right of and below its frame, as PrimeHUD places its own.
        image(layouts, 2, name, new int[]{slot[0] + 1, slot[1] + 1}, shown == null ? List.of() : List.of(shown));
        if (warns) {
            image(layouts, 3, name + "_shine", slot, shown == null ? List.of(low) : List.of(low, shown));
        }
        huds.append('\n').append(name).append(":\n")
                .append("  default: true\n")
                .append("  layouts:\n")
                .append("    1:\n")
                .append("      name: ").append(name).append('\n')
                .append("      gui:\n")
                .append("        x: ").append(panel.gui().x()).append('\n')
                .append("        y: ").append(panel.gui().y()).append('\n')
                .append("      pixel:\n")
                .append("        x: ").append(panel.pixel().x()).append('\n')
                .append("        y: ").append(panel.pixel().y()).append('\n');
        return warns;
    }

    /** One image of a layout, at {@code slot}, shown while every one of {@code conditions} holds. */
    private static void image(StringBuilder layouts, int index, String name, int[] slot, List<String> conditions) {
        layouts.append("    ").append(index).append(":\n")
                .append("      name: ").append(name).append('\n')
                .append("      x: ").append(slot[0]).append('\n')
                .append("      y: ").append(slot[1]).append('\n');
        if (!conditions.isEmpty()) {
            layouts.append("      conditions:\n");
            for (int i = 0; i < conditions.size(); i++) {
                layouts.append("        ").append(i + 1).append(":\n").append(conditions.get(i));
                if (i < conditions.size() - 1) {
                    layouts.append("          gate: and\n");
                }
            }
        }
    }

    /** A condition of BetterHud's on a number: {@code first operation second}, as its layouts write it. */
    private static String condition(String first, String number, String operation) {
        return "          first: " + quote(first) + "\n"
                + "          second: \"'(number)" + number + "'\"\n"
                + "          operation: '" + operation + "'\n";
    }

    /** 3.1: a bar as a row of its own icons, where the action bar drew it. */
    private static void icons(Bar bar, boolean legacyOffset, Map<String, byte[]> files, StringBuilder images,
                              StringBuilder layouts, StringBuilder huds) throws IOException {
        HudDefinition hud = bar.hud().definition();
        String name = name(hud);
        String file = hud.namespace() + "_" + hud.id();
        int step = step(bar.hud());
        BufferedImage full = strip(bar.full(), hud.height(), step, hud.segments());
        files.put(ASSETS + file + "_bar.png", png(full));
        boolean background = bar.empty() != null;
        if (background) {
            files.put(ASSETS + file + "_empty.png", png(strip(bar.empty(), hud.height(), step, hud.segments())));
            images.append('\n').append(name).append("_empty:\n")
                    .append("  type: single\n")
                    .append("  file: ").append(quote(NAME + "/" + file + "_empty.png")).append('\n');
        }
        images.append('\n').append(name).append(":\n")
                .append("  type: listener\n")
                .append("  file: ").append(quote(NAME + "/" + file + "_bar.png")).append('\n')
                .append("  split: ").append(bar.half() != null ? hud.segments() * 2 : hud.segments()).append('\n')
                .append("  split-type: ").append(fromRight(hud) ? "right" : "left").append('\n')
                .append("  setting:\n")
                .append("    listener:\n")
                .append("      class: placeholder\n")
                .append("      value: ").append(quote("(number)papi:" + NAME + "_hud_" + bar.placeholder() + "_value"))
                .append('\n')
                .append("      max: ").append(quote("(number)papi:" + NAME + "_hud_" + bar.placeholder() + "_max"))
                .append('\n');
        layouts.append('\n').append(name).append(":\n").append("  images:\n");
        int index = 1;
        if (background) {
            layouts.append("    ").append(index++).append(":\n")
                    .append("      name: ").append(name).append("_empty\n")
                    .append("      x: 0\n      y: 0\n");
        }
        layouts.append("    ").append(index).append(":\n")
                .append("      name: ").append(name).append('\n')
                .append("      x: 0\n      y: 0\n")
                .append("      layer: 1\n");
        Place place = place(bar.hud(), legacyOffset, full.getWidth());
        huds.append('\n').append(name).append(":\n")
                .append("  default: true\n")
                .append("  layouts:\n")
                .append("    1:\n")
                .append("      name: ").append(name).append('\n')
                .append("      gui:\n")
                .append("        x: ").append(place.gui().x()).append('\n')
                .append("        y: ").append(place.gui().y()).append('\n')
                .append("      pixel:\n")
                .append("        x: ").append(place.pixel().x()).append('\n')
                .append("        y: ").append(place.pixel().y()).append('\n');
    }

    /**
     * Where a bar goes: the place its {@code betterhud:} gives - in BetterHud's own terms, as its
     * HUD files take them - or where the action bar drew it: against the hotbar for an anchored bar,
     * its ascent and offset from the action bar's line for another, measured from the middle of the
     * bottom of the screen to its top left corner, and put back down by BetterHud's legacy offset
     * when it has one. BetterHud centres an image on its {@code x} (an {@code align:} on it changes
     * nothing in 2.0.0), so the bar's {@code x} is its left end plus half its width.
     *
     * @param width the bar's image, in pixels
     */
    public static Place place(HudLayout layout, boolean legacyOffset, int width) {
        HudDefinition hud = layout.definition();
        HudDefinition.BetterHud own = hud.betterHud();
        HudAnchor anchor = hud.anchor();
        int x;
        int y;
        if (anchor != null) {
            x = anchor.offset(drawnWidth(layout));
            y = anchor.top();
        } else {
            x = hud.offset();
            y = HudAnchor.ACTION_BAR_TOP + HudAnchor.GLYPH_BASELINE - hud.ascent();
        }
        HudDefinition.Spot gui = own != null && own.gui() != null ? own.gui() : BOTTOM_MIDDLE;
        HudDefinition.Spot pixel = own != null && own.pixel() != null ? own.pixel()
                : new HudDefinition.Spot(x + width / 2, y + (legacyOffset ? LEGACY_OFFSET : 0));
        return new Place(gui, pixel);
    }

    /** From one icon to the next, as the action bar draws them: an icon's advance, plus the spacing. */
    static int step(HudLayout layout) {
        return Math.max(1, layout.glyphs().fullAdvance() + layout.definition().spacing());
    }

    /** The bar full, from its first pixel to its last. */
    static int drawnWidth(HudLayout layout) {
        int segments = layout.definition().segments();
        return (segments - 1) * step(layout) + Math.max(1, layout.glyphs().fullAdvance() - 1);
    }

    /** A food-side bar empties from the left, as food does: what is left of it is on the right. */
    private static boolean fromRight(HudDefinition hud) {
        return hud.anchor() != null && hud.anchor().right();
    }

    /** {@code segments} copies of an icon, {@code step} pixels apart, the icon drawn {@code height} tall. */
    static BufferedImage strip(BufferedImage icon, int height, int step, int segments) {
        BufferedImage scaled = scaled(icon, height);
        int width = (segments - 1) * step + scaled.getWidth();
        BufferedImage strip = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = strip.createGraphics();
        try {
            for (int i = 0; i < segments; i++) {
                graphics.drawImage(scaled, i * step, 0, null);
            }
        } finally {
            graphics.dispose();
        }
        return strip;
    }

    /** The icon as the font draws it: {@code height} pixels tall, its width in proportion, pixels kept sharp. */
    private static BufferedImage scaled(BufferedImage icon, int height) {
        if (icon.getHeight() == height && icon.getType() == BufferedImage.TYPE_INT_ARGB) {
            return icon;
        }
        int width = Math.max(1, (int) Math.round(icon.getWidth() * (double) height / icon.getHeight()));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            graphics.drawImage(icon, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("no PNG writer");
        }
        return out.toByteArray();
    }

    private static String quote(String text) {
        return '"' + text.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    /** For log lines: the bars, by id. */
    public static String names(List<Bar> bars) {
        return String.join(", ", bars.stream().map(bar -> bar.hud().definition().id().toLowerCase(Locale.ROOT)).toList());
    }
}
