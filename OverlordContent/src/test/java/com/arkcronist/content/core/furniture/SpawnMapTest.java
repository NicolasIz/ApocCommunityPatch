package com.arkcronist.content.core.furniture;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 3.2: the map board's picture - the world from above, on parchment. */
class SpawnMapTest {

    private static final int GRASS = 0x7FB238;
    private static final int WATER = 0x4040FF;

    /** A green hill in the west, a lake in the east, nothing explored along the bottom rows. */
    private static SpawnMap.Column[][] world() {
        SpawnMap.Column[][] columns = new SpawnMap.Column[SpawnMap.HEIGHT][SpawnMap.WIDTH];
        for (int y = 0; y < SpawnMap.HEIGHT - 20; y++) {
            for (int x = 0; x < SpawnMap.WIDTH; x++) {
                columns[y][x] = x < SpawnMap.WIDTH / 2
                        ? new SpawnMap.Column(GRASS, 64 + y / 10, 0)
                        : new SpawnMap.Column(WATER, 62, x < SpawnMap.WIDTH * 3 / 4 ? 1 : 10);
            }
        }
        return columns;
    }

    @Test
    void itIsTheBoardsSizeWithADoubleBorder() {
        BufferedImage map = SpawnMap.draw(world(), 100, 60);
        assertEquals(SpawnMap.WIDTH, map.getWidth());
        assertEquals(SpawnMap.HEIGHT, map.getHeight());
        assertEquals(20.0 / 11, SpawnMap.WIDTH / (double) SpawnMap.HEIGHT, 0.01, "twenty to eleven, as the canvas");
        int ink = map.getRGB(0, 80);
        assertEquals(ink, map.getRGB(1, 80));
        assertEquals(ink, map.getRGB(4, 80), "the inner line");
        assertNotEquals(ink, map.getRGB(3, 80));
        assertEquals(0xFF, ink >>> 24);
    }

    @Test
    void groundClimbingFromTheNorthIsLitAndDeepWaterIsDark() {
        BufferedImage map = SpawnMap.draw(world(), -1, -1);
        // Row 30 is a step up from row 29 (y / 10 grows): lit; row 31 is level: in between.
        // Averaged along the row: the paper's grain varies each pixel a little.
        int climbing = brightness(map, 20, 140, 30);
        int level = brightness(map, 20, 140, 31);
        assertTrue(climbing > level, climbing + " should be brighter than " + level);
        int shallow = brightness(map, 170, 230, 50);
        int deep = brightness(map, 250, 290, 50);
        assertTrue(shallow > deep, "shallow " + shallow + " over deep " + deep);
        assertTrue(blue(map.getRGB(280, 50)) > red(map.getRGB(280, 50)), "water still looks like water");
        assertTrue(green(map.getRGB(60, 50)) > blue(map.getRGB(60, 50)), "grass still looks like grass");
    }

    @Test
    void unexploredGroundIsBareParchmentAndTheSpawnIsMarked() {
        BufferedImage map = SpawnMap.draw(world(), 100, 60);
        int parchment = map.getRGB(160, SpawnMap.HEIGHT - 17);
        assertTrue(red(parchment) > 190 && green(parchment) > 170 && blue(parchment) > 120,
                Integer.toHexString(parchment) + " is parchment");
        int mark = map.getRGB(100, 60);
        assertTrue(red(mark) > 150 && green(mark) < 80, Integer.toHexString(mark) + " is the red cross");
        BufferedImage unmarked = SpawnMap.draw(world(), -1, -1);
        assertNotEquals(mark, unmarked.getRGB(100, 60));
        // The compass's north point, red too, at the top right.
        int north = map.getRGB(SpawnMap.WIDTH - 22, 21 - 6);
        assertTrue(red(north) > 150 && green(north) < 80, Integer.toHexString(north));
    }

    @Test
    void theSameWorldDrawsTheSamePicture() {
        BufferedImage first = SpawnMap.draw(world(), 100, 60);
        BufferedImage second = SpawnMap.draw(world(), 100, 60);
        for (int y = 0; y < SpawnMap.HEIGHT; y += 7) {
            for (int x = 0; x < SpawnMap.WIDTH; x += 5) {
                assertEquals(first.getRGB(x, y), second.getRGB(x, y));
            }
        }
    }

    private static int red(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int brightness(BufferedImage map, int fromX, int toX, int y) {
        long sum = 0;
        for (int x = fromX; x < toX; x++) {
            int argb = map.getRGB(x, y);
            sum += red(argb) + green(argb) + blue(argb);
        }
        return (int) (sum / (toX - fromX));
    }
}
