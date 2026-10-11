package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.definition.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

/**
 * An inventory icon drawn from a model (3.0), for Bedrock: an item there is a picture, so a 3D model
 * - a piece of furniture, a block - is drawn the way Java shows it in a slot. The model is turned
 * by its {@code display.gui} rotation (vanilla's 30 / 225 for a block), scaled, projected straight
 * on, each face shaded as Java shades a block's sides, nearest pixel first; then halved for smooth
 * edges.
 */
public final class IconRenderer {

    /** The icon's size; it is drawn at twice this and halved. */
    public static final int SIZE = 32;
    private static final int DRAW = SIZE * 2;

    private IconRenderer() {
    }

    /**
     * @param textures a texture's image, or null when the pack has none; only the first frame of an
     *                 animated one is used
     * @return null for a model with nothing to draw
     */
    public static @Nullable BufferedImage render(JavaModel model, Function<ResourceLocation, BufferedImage> textures) {
        if (model.elements().isEmpty()) {
            return null;
        }
        float[] rotation = model.guiRotation();
        Matrix3f turn = new Matrix3f().rotationXYZ((float) Math.toRadians(rotation[0]),
                (float) Math.toRadians(rotation[1]), (float) Math.toRadians(rotation[2]));
        float scale = model.guiScale() <= 0 ? 0.625f : model.guiScale();
        int[] colour = new int[DRAW * DRAW];
        float[] depth = new float[DRAW * DRAW];
        Arrays.fill(depth, Float.NEGATIVE_INFINITY);
        boolean drew = false;
        for (JavaModel.Element element : model.elements()) {
            Matrix3f own = element.rotated() ? ownTurn(element) : null;
            for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> face : element.faces().entrySet()) {
                BufferedImage image = textures.apply(face.getValue().texture());
                if (image == null) {
                    continue;
                }
                Vector3f[] corners = corners(face.getKey(), element.from(), element.to());
                for (int i = 0; i < corners.length; i++) {
                    Vector3f corner = corners[i];
                    if (own != null) {
                        corner.sub(element.origin()[0], element.origin()[1], element.origin()[2]);
                        own.transform(corner);
                        corner.add(element.origin()[0], element.origin()[1], element.origin()[2]);
                    }
                    corner.sub(8, 8, 8).div(16).mul(scale);
                    turn.transform(corner);
                }
                drew |= fill(corners, face.getValue().uv(), image, shade(face.getKey()), colour, depth);
            }
        }
        return drew ? halve(colour) : null;
    }

    /** A flat item's picture: its layers over each other, each its first frame, at the size of the first. */
    public static @Nullable BufferedImage flat(java.util.List<BufferedImage> layers) {
        if (layers.isEmpty()) {
            return null;
        }
        BufferedImage first = frame(layers.get(0));
        BufferedImage out = new BufferedImage(first.getWidth(), first.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = out.createGraphics();
        for (BufferedImage layer : layers) {
            graphics.drawImage(frame(layer), 0, 0, out.getWidth(), out.getHeight(), null);
        }
        graphics.dispose();
        return out;
    }

    /** The first frame of an animated texture - frames stack downwards - or the image itself. */
    public static BufferedImage frame(BufferedImage image) {
        return image.getHeight() > image.getWidth() && image.getHeight() % image.getWidth() == 0
                ? image.getSubimage(0, 0, image.getWidth(), image.getWidth()) : image;
    }

    private static Matrix3f ownTurn(JavaModel.Element element) {
        float angle = (float) Math.toRadians(element.angle());
        return switch (element.axis()) {
            case 'x' -> new Matrix3f().rotationX(angle);
            case 'y' -> new Matrix3f().rotationY(angle);
            default -> new Matrix3f().rotationZ(angle);
        };
    }

    /** The face's corners: u along the first edge, v along the second, as Java lays its texture. */
    private static Vector3f[] corners(JavaModel.Face face, float[] f, float[] t) {
        return switch (face) {
            case NORTH -> quad(t[0], t[1], f[2], -1, 0, 0, t[0] - f[0], 0, -1, 0, t[1] - f[1]);
            case SOUTH -> quad(f[0], t[1], t[2], 1, 0, 0, t[0] - f[0], 0, -1, 0, t[1] - f[1]);
            case EAST -> quad(t[0], t[1], t[2], 0, 0, -1, t[2] - f[2], 0, -1, 0, t[1] - f[1]);
            case WEST -> quad(f[0], t[1], f[2], 0, 0, 1, t[2] - f[2], 0, -1, 0, t[1] - f[1]);
            case UP -> quadFlat(f[0], t[1], f[2], t[0] - f[0], 1, t[2] - f[2]);
            case DOWN -> quadFlat(f[0], f[1], t[2], t[0] - f[0], -1, t[2] - f[2]);
        };
    }

    private static Vector3f[] quad(float x, float y, float z, float ux, float uy, float uz, float ulen,
                                   float vx, float vy, float vz, float vlen) {
        Vector3f origin = new Vector3f(x, y, z);
        Vector3f u = new Vector3f(ux, uy, uz).mul(ulen);
        Vector3f v = new Vector3f(vx, vy, vz).mul(vlen);
        return new Vector3f[]{new Vector3f(origin), new Vector3f(origin).add(u), new Vector3f(origin).add(v)};
    }

    /** Up: u east, v south. Down: u east, v north - the corner at the south edge comes first. */
    private static Vector3f[] quadFlat(float x, float y, float z, float ulen, float vSign, float vlen) {
        Vector3f origin = new Vector3f(x, y, z);
        Vector3f u = new Vector3f(ulen, 0, 0);
        Vector3f v = new Vector3f(0, 0, vSign * vlen);
        return new Vector3f[]{new Vector3f(origin), new Vector3f(origin).add(u), new Vector3f(origin).add(v)};
    }

    /** Java's shading of a block's faces in an inventory, near enough. */
    private static float shade(JavaModel.Face face) {
        return switch (face) {
            case UP -> 1f;
            case DOWN -> 0.5f;
            case NORTH, SOUTH -> 0.8f;
            case EAST, WEST -> 0.62f;
        };
    }

    /**
     * Fills the parallelogram {@code corners[0]}, +u ({@code [1]}), +v ({@code [2]}): for each pixel,
     * where on the face it is, what the texture has there, and whether it is nearer than what is drawn.
     */
    private static boolean fill(Vector3f[] corners, float[] uv, BufferedImage texture, float shade, int[] colour,
                                float[] depth) {
        BufferedImage image = frame(texture);
        float ox = (corners[0].x + 0.5f) * DRAW;
        float oy = (0.5f - corners[0].y) * DRAW;
        float ux = (corners[1].x - corners[0].x) * DRAW;
        float uy = -(corners[1].y - corners[0].y) * DRAW;
        float vx = (corners[2].x - corners[0].x) * DRAW;
        float vy = -(corners[2].y - corners[0].y) * DRAW;
        float det = ux * vy - uy * vx;
        if (Math.abs(det) < 1e-6f) {
            return false;
        }
        float uz = corners[1].z - corners[0].z;
        float vz = corners[2].z - corners[0].z;
        int minX = (int) Math.max(0, Math.floor(Math.min(Math.min(ox, ox + ux), Math.min(ox + vx, ox + ux + vx))));
        int maxX = (int) Math.min(DRAW - 1, Math.ceil(Math.max(Math.max(ox, ox + ux), Math.max(ox + vx, ox + ux + vx))));
        int minY = (int) Math.max(0, Math.floor(Math.min(Math.min(oy, oy + uy), Math.min(oy + vy, oy + uy + vy))));
        int maxY = (int) Math.min(DRAW - 1, Math.ceil(Math.max(Math.max(oy, oy + uy), Math.max(oy + vy, oy + uy + vy))));
        boolean drew = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f - ox;
                float py = y + 0.5f - oy;
                float s = (px * vy - py * vx) / det;
                float t = (ux * py - uy * px) / det;
                if (s < 0 || s > 1 || t < 0 || t > 1) {
                    continue;
                }
                float z = corners[0].z + s * uz + t * vz;
                int at = y * DRAW + x;
                if (z <= depth[at]) {
                    continue;
                }
                float u = (uv[0] + s * (uv[2] - uv[0])) / 16f;
                float v = (uv[1] + t * (uv[3] - uv[1])) / 16f;
                int tx = Math.min(image.getWidth() - 1, Math.max(0, (int) Math.floor(u * image.getWidth())));
                int ty = Math.min(image.getHeight() - 1, Math.max(0, (int) Math.floor(v * image.getHeight())));
                int argb = image.getRGB(tx, ty);
                if ((argb >>> 24) < 128) {
                    continue;
                }
                int r = (int) (((argb >> 16) & 0xff) * shade);
                int g = (int) (((argb >> 8) & 0xff) * shade);
                int b = (int) ((argb & 0xff) * shade);
                colour[at] = 0xff000000 | r << 16 | g << 8 | b;
                depth[at] = z;
                drew = true;
            }
        }
        return drew;
    }

    /** Each 2 x 2 block of the drawing averaged into one pixel. */
    private static BufferedImage halve(int[] colour) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int c = colour[(2 * y + dy) * DRAW + 2 * x + dx];
                        int alpha = c >>> 24;
                        a += alpha;
                        r += ((c >> 16) & 0xff) * alpha;
                        g += ((c >> 8) & 0xff) * alpha;
                        b += (c & 0xff) * alpha;
                    }
                }
                if (a > 0) {
                    out.setRGB(x, y, (a / 4) << 24 | (r / a) << 16 | (g / a) << 8 | (b / a));
                }
            }
        }
        return out;
    }
}
