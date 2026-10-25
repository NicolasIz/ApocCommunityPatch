package com.arkcronist.content.core.geyser;

import com.arkcronist.content.core.definition.Placement;
import com.arkcronist.content.core.furniture.Footprint;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A piece of furniture as Bedrock blocks (3.0): its model, placed exactly as the Java client draws
 * it, cut at the block boundaries into one piece of geometry per block it reaches.
 *
 * <p>Java draws furniture with an item display at the middle of the block it stands on (shifted
 * for an even width, lowered for a Blockbench model): the display's transformation - translation,
 * scale - over the model centred on its origin, after the item display's own half turn about y,
 * and the whole turned to the piece's yaw. Here it is laid out facing south (yaw 0) in the frame of
 * the block it was placed on: pixels, origin at the middle of that block's floor. Every cube is cut
 * where it crosses into the next block, and each part keeps the stretch of its faces' textures that
 * falls on it; a cube turned about an axis is not cut, but goes whole to the block its middle is
 * in. Each block's cubes are then in that block's own frame, within the block.</p>
 *
 * <p>Other yaws are not laid out again: a Bedrock block turns its geometry about its own middle
 * ({@code minecraft:transformation}), so the same pieces serve every quarter turn, each moved to
 * where {@link #turned} puts it.</p>
 */
public final class FurnitureLayout {

    /** A block of the piece, relative to the one it was placed on, as laid out facing south. */
    public record Cell(int dx, int dy, int dz) implements Comparable<Cell> {

        @Override
        public int compareTo(Cell other) {
            int c = Integer.compare(dy, other.dy);
            if (c == 0) {
                c = Integer.compare(dx, other.dx);
            }
            return c != 0 ? c : Integer.compare(dz, other.dz);
        }

        public String key() {
            return dx + "," + dy + "," + dz;
        }
    }

    /**
     * @param cells each block's cubes, in that block's frame
     * @param notes what could not be carried over exactly, for the log
     */
    public record Layout(Map<Cell, List<BedrockCube>> cells, List<String> notes) {
    }

    private static final float EPSILON = 1e-4f;

    private FurnitureLayout() {
    }

    public static Layout lay(JavaModel model, Footprint footprint, Placement.Display display, boolean animated) {
        List<String> notes = new ArrayList<>();
        // The display's own half turn about y, and a display rotation of half a turn about y, cancel.
        Placement.Vec3 turn = display.rotation();
        boolean undo = false;
        if (turn.x() != 0 || turn.z() != 0 || (turn.y() % 360 != 0 && Math.abs(turn.y() % 360) != 180)) {
            notes.add("its display rotation " + turn.x() + ", " + turn.y() + ", " + turn.z()
                    + " is not carried over: Bedrock blocks turn by quarter turns about y only");
        } else {
            undo = Math.abs(turn.y() % 360) == 180;
        }
        if (!"NONE".equals(display.transform())) {
            notes.add("its display transform " + display.transform() + " is drawn as NONE");
        }
        float flip = undo ? 1 : -1;
        Placement.Vec3 scale = display.scale();
        float[] gain = {flip * Math.abs(scale.x()), Math.abs(scale.y()), flip * Math.abs(scale.z())};
        double[] shift = footprint.centreShift(0);
        float[] offset = {(float) (16 * shift[0] + 16 * display.translation().x()),
                (animated ? 0 : 8) + 16 * display.translation().y(),
                (float) (16 * shift[1] + 16 * display.translation().z())};

        Map<Cell, List<BedrockCube>> cells = new LinkedHashMap<>();
        for (JavaModel.Element element : model.elements()) {
            float[] min = new float[3];
            float[] max = new float[3];
            for (int axis = 0; axis < 3; axis++) {
                float a = place(element.from()[axis], axis, gain, offset);
                float b = place(element.to()[axis], axis, gain, offset);
                min[axis] = Math.min(a, b);
                max[axis] = Math.max(a, b);
            }
            if (element.rotated()) {
                float angle = element.axis() == 'y' ? element.angle() : flip * element.angle();
                float[] pivot = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    pivot[axis] = place(element.origin()[axis], axis, gain, offset);
                }
                Map<JavaModel.Face, BedrockCube.Face> faces = faces(element, element.from(), element.to(), gain, undo);
                add(cells, new BedrockCube(min, max, faces, element.axis(), angle, pivot));
                continue;
            }
            for (float[][] part : cut(min, max)) {
                float[] from = new float[3];
                float[] to = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    float a = unplace(part[0][axis], axis, gain, offset);
                    float b = unplace(part[1][axis], axis, gain, offset);
                    from[axis] = Math.min(a, b);
                    to[axis] = Math.max(a, b);
                }
                Map<JavaModel.Face, BedrockCube.Face> faces = faces(element, from, to, gain, undo);
                if (!faces.isEmpty()) {
                    add(cells, new BedrockCube(part[0], part[1], faces, null, 0, new float[]{0, 0, 0}));
                }
            }
        }
        return new Layout(cells, notes);
    }

    /**
     * Where a cell of a piece laid out facing south ends up for one turned {@code quarter} quarter
     * turns (yaw 90 times that), relative to the block it was placed on: the piece turns about its
     * own middle, which an even width shifts.
     */
    public static Cell turned(Cell cell, Footprint footprint, int quarter) {
        double[] from = footprint.centreShift(0);
        double[] to = footprint.centreShift(quarter * 90f);
        double x = cell.dx() - from[0];
        double z = cell.dz() - from[1];
        // The display turns by -yaw about y, right-handed.
        double angle = Math.toRadians(-quarter * 90.0);
        double cos = Math.round(Math.cos(angle));
        double sin = Math.round(Math.sin(angle));
        double tx = x * cos + z * sin + to[0];
        double tz = -x * sin + z * cos + to[1];
        return new Cell((int) Math.round(tx), cell.dy(), (int) Math.round(tz));
    }

    /** A model coordinate in the frame of the block the piece stands on. */
    private static float place(float model, int axis, float[] gain, float[] offset) {
        return offset[axis] + gain[axis] * (model - 8);
    }

    private static float unplace(float placed, int axis, float[] gain, float[] offset) {
        return 8 + (placed - offset[axis]) / gain[axis];
    }

    /** The box cut where it crosses a block boundary: x and z at -8 + 16n, y at 16n. */
    private static List<float[][]> cut(float[] min, float[] max) {
        List<float[][]> parts = new ArrayList<>();
        parts.add(new float[][]{min.clone(), max.clone()});
        for (int axis = 0; axis < 3; axis++) {
            float base = axis == 1 ? 0 : -8;
            List<float[][]> next = new ArrayList<>();
            for (float[][] part : parts) {
                TreeSet<Float> planes = new TreeSet<>();
                double first = Math.ceil((part[0][axis] - base) / 16.0 + EPSILON);
                for (double n = first; base + 16 * n < part[1][axis] - EPSILON; n++) {
                    planes.add((float) (base + 16 * n));
                }
                float start = part[0][axis];
                for (float plane : planes) {
                    float[][] piece = {part[0].clone(), part[1].clone()};
                    piece[0][axis] = start;
                    piece[1][axis] = plane;
                    next.add(piece);
                    start = plane;
                }
                float[][] last = {part[0].clone(), part[1].clone()};
                last[0][axis] = start;
                next.add(last);
            }
            parts = next;
        }
        return parts;
    }

    /**
     * The faces of the part of {@code element} from {@code from} to {@code to} (model pixels) that lie
     * on the element's own faces, each with its stretch of texture, named for the way they face once
     * placed.
     */
    private static Map<JavaModel.Face, BedrockCube.Face> faces(JavaModel.Element element, float[] from, float[] to,
                                                               float[] gain, boolean undo) {
        Map<JavaModel.Face, BedrockCube.Face> faces = new EnumMap<>(JavaModel.Face.class);
        for (Map.Entry<JavaModel.Face, JavaModel.FaceTexture> entry : element.faces().entrySet()) {
            JavaModel.Face face = entry.getKey();
            if (!onFace(face, element, from, to)) {
                continue;
            }
            JavaModel.FaceTexture texture = entry.getValue();
            float[] uv = subUv(face, texture.uv(), element.from(), element.to(), from, to);
            JavaModel.Face placed = face;
            if (!undo) {
                // Half a turn about y: each side faces the other way, the top's picture turns round.
                placed = switch (face) {
                    case NORTH -> JavaModel.Face.SOUTH;
                    case SOUTH -> JavaModel.Face.NORTH;
                    case EAST -> JavaModel.Face.WEST;
                    case WEST -> JavaModel.Face.EAST;
                    default -> face;
                };
                if (face == JavaModel.Face.UP || face == JavaModel.Face.DOWN) {
                    uv = new float[]{uv[2], uv[3], uv[0], uv[1]};
                }
            }
            faces.put(placed, new BedrockCube.Face(uv, texture.texture()));
        }
        return faces;
    }

    /** Whether the part's side towards {@code face} is the element's own face, not a cut through it. */
    private static boolean onFace(JavaModel.Face face, JavaModel.Element element, float[] from, float[] to) {
        return switch (face) {
            case DOWN -> Math.abs(from[1] - element.from()[1]) < EPSILON;
            case UP -> Math.abs(to[1] - element.to()[1]) < EPSILON;
            case NORTH -> Math.abs(from[2] - element.from()[2]) < EPSILON;
            case SOUTH -> Math.abs(to[2] - element.to()[2]) < EPSILON;
            case WEST -> Math.abs(from[0] - element.from()[0]) < EPSILON;
            case EAST -> Math.abs(to[0] - element.to()[0]) < EPSILON;
        };
    }

    /**
     * The stretch of a face's UV a part covers. How Java lays a texture on a face: on north, u runs
     * east to west and v top to bottom; on south, west to east; on east, south to north; on west,
     * north to south; on up, u west to east and v north to south; on down, v south to north.
     */
    static float[] subUv(JavaModel.Face face, float[] uv, float[] from0, float[] to0, float[] from, float[] to) {
        float[] u = switch (face) {
            case NORTH -> span(to0[0], from0[0], to[0], from[0]);
            case SOUTH, UP, DOWN -> span(from0[0], to0[0], from[0], to[0]);
            case EAST -> span(to0[2], from0[2], to[2], from[2]);
            case WEST -> span(from0[2], to0[2], from[2], to[2]);
        };
        float[] v = switch (face) {
            case UP -> span(from0[2], to0[2], from[2], to[2]);
            case DOWN -> span(to0[2], from0[2], to[2], from[2]);
            default -> span(to0[1], from0[1], to[1], from[1]);
        };
        return new float[]{lerp(uv[0], uv[2], u[0]), lerp(uv[1], uv[3], v[0]),
                lerp(uv[0], uv[2], u[1]), lerp(uv[1], uv[3], v[1])};
    }

    /** Where {@code a} and {@code b} fall from {@code start} to {@code end}, 0 to 1. */
    private static float[] span(float start, float end, float a, float b) {
        float length = end - start;
        if (Math.abs(length) < EPSILON) {
            return new float[]{0, 1};
        }
        return new float[]{(a - start) / length, (b - start) / length};
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static void add(Map<Cell, List<BedrockCube>> cells, BedrockCube cube) {
        float[] centre = cube.centre();
        int cx = (int) Math.floor((centre[0] + 8) / 16);
        int cy = (int) Math.floor(centre[1] / 16);
        int cz = (int) Math.floor((centre[2] + 8) / 16);
        cells.computeIfAbsent(new Cell(cx, cy, cz), ignored -> new ArrayList<>())
                .add(cube.moved(-16 * cx, -16 * cy, -16 * cz));
    }
}
