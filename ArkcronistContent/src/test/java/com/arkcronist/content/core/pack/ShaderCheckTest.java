package com.arkcronist.content.core.pack;

import com.arkcronist.content.core.pack.ShaderCheck.Finding;
import com.arkcronist.content.core.pack.ShaderCheck.Mode;
import com.arkcronist.content.core.pack.ShaderCheck.Report;
import com.arkcronist.content.core.pack.ShaderCheck.Severity;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2.0: shaders read against what the 1.21.8 client does with them. */
class ShaderCheckTest {

    private static final String TERRAIN_VSH = "assets/minecraft/shaders/core/terrain.vsh";
    private static final String TERRAIN_FSH = "assets/minecraft/shaders/core/terrain.fsh";

    /** A shader written for 1.21.4: loose uniforms, and a core shader 1.21.5 merged into terrain. */
    private static final String OLD_TRANSLUCENT = """
            #version 150
            #moj_import <fog.glsl>

            in vec3 Position;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            uniform vec3 ChunkOffset;
            uniform sampler2D Sampler2;

            void main() {
                gl_Position = ProjMat * ModelViewMat * vec4(Position + ChunkOffset, 1.0);
            }
            """;

    private static String terrain(String name) throws IOException {
        try (InputStream in = ShaderCheckTest.class.getResourceAsStream("/shaders/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The facts are the client's: the terrain pipeline declares transforms, projection and fog; Globals is built in. */
    @Test
    void theFactsAreThoseOfThe1218Client() {
        assertEquals("1.21.8", ShaderCheck.clientVersion());
        assertEquals(List.of("DynamicTransforms", "Fog", "Projection"), ShaderCheck.bound("core/terrain.vsh"));
        assertTrue(ShaderCheck.members("Globals").contains("GameTime"));
        assertTrue(ShaderCheck.members("DynamicTransforms").contains("ModelViewMat"));
    }

    /** A terrain shader pair with the 1.21.8 client's interface - its imports, blocks, inputs and outputs. */
    @Test
    void aTerrainShaderWithTheClientsInterfacePasses() throws IOException {
        Report report = ShaderCheck.check(Map.of(TERRAIN_VSH, terrain("terrain-like.vsh"),
                TERRAIN_FSH, terrain("terrain-like.fsh")), Mode.DROP_BROKEN);
        assertEquals(List.of(), report.findings());
        assertEquals(Set.of(), report.drop());
    }

    /** What an ItemsAdder pack of the 1.21.4 days brings: never loaded, and broken if it were. */
    @Test
    void aShaderWrittenFor1214IsBrokenAndNoLongerLoaded() {
        String path = "assets/minecraft/shaders/core/rendertype_translucent.vsh";
        Report report = ShaderCheck.check(Map.of(path, OLD_TRANSLUCENT), Mode.KEEP);
        assertEquals(List.of(Severity.UNUSED, Severity.BROKEN), report.findings().stream().map(Finding::severity).toList());
        String broken = report.findings().get(1).message();
        assertTrue(broken.contains("'uniform mat4 ModelViewMat;'") && broken.contains("DynamicTransforms")
                && broken.contains("#moj_import <minecraft:dynamictransforms.glsl>"), broken);
        assertTrue(broken.contains("ProjMat is in the Projection block"), broken);
        assertTrue(!broken.contains("Sampler2"), "samplers are still set one by one");
        assertEquals(Set.of(), report.drop(), "keep only reports");
    }

    /**
     * GameTime in terrain - an animated liquid surface, say - is fine: the client binds Globals to
     * any shader that declares it. A block it never fills - one only clouds are given - is reported.
     */
    @Test
    void globalsReachTerrainButABlockNoPipelineFillsIsInert() throws IOException {
        String withTime = terrain("terrain-like.vsh").replace("#moj_import <minecraft:fog.glsl>",
                "#moj_import <minecraft:fog.glsl>\n#moj_import <minecraft:globals.glsl>")
                .replace("texCoord0 = UV0;", "texCoord0 = UV0 + vec2(GameTime, 0.0);");
        assertEquals(List.of(), ShaderCheck.check(Map.of(TERRAIN_VSH, withTime), Mode.DROP_BROKEN).findings());

        String withClouds = terrain("terrain-like.vsh").replace("void main() {",
                "layout(std140) uniform CloudInfo {\n    vec4 CloudColor;\n    vec3 CloudOffset;\n    vec3 CellSize;\n};\n\nvoid main() {");
        Report report = ShaderCheck.check(Map.of(TERRAIN_VSH, withClouds), Mode.DROP_BROKEN);
        assertEquals(1, report.findings().size());
        Finding inert = report.findings().get(0);
        assertEquals(Severity.INERT, inert.severity());
        assertTrue(inert.message().contains("CloudInfo") && inert.message().contains("Found unknown and unsupported uniform"),
                inert.message());
        assertEquals(Set.of(), report.drop(), "it compiles and runs; only the block is never filled");
    }

    @Test
    void anImportNothingProvidesIsBrokenAndOneThePackProvidesIsFine() throws IOException {
        String importing = terrain("terrain-like.fsh").replace("#moj_import <minecraft:fog.glsl>",
                "#moj_import <minecraft:fog.glsl>\n#moj_import <demo:liquid_waves.glsl>");
        Report missing = ShaderCheck.check(Map.of(TERRAIN_FSH, importing), Mode.KEEP);
        assertEquals(Severity.BROKEN, missing.findings().get(0).severity());
        assertTrue(missing.findings().get(0).message().contains("<demo:liquid_waves.glsl>"));

        Report provided = ShaderCheck.check(Map.of(TERRAIN_FSH, importing,
                "assets/demo/shaders/include/liquid_waves.glsl", "#version 150\nfloat waves(vec2 uv) { return uv.x; }\n"),
                Mode.KEEP);
        assertEquals(List.of(), provided.findings());
    }

    /** drop-broken leaves out the broken stage and its other half, so no pair is left mismatched; drop, all. */
    @Test
    void dropBrokenTakesBothHalvesOfAProgramAndDropTakesEverything() throws IOException {
        String fsh = "assets/minecraft/shaders/core/entity.fsh";
        String vsh = "assets/minecraft/shaders/core/entity.vsh";
        Map<String, String> pack = Map.of(fsh, "void main() {}\n", vsh, "#version 150\nvoid main() {}\n",
                TERRAIN_VSH, terrain("terrain-like.vsh"));
        Report report = ShaderCheck.check(pack, Mode.DROP_BROKEN);
        assertEquals(Set.of(fsh, vsh), report.drop());
        assertTrue(report.findings().get(0).message().contains("#version"));
        assertEquals(pack.keySet(), ShaderCheck.check(pack, Mode.DROP).drop());
    }

    @Test
    void aCoreShaderOutsideTheMinecraftNamespaceIsNeverLoaded() throws IOException {
        Report report = ShaderCheck.check(Map.of("assets/demo/shaders/core/terrain.vsh", terrain("terrain-like.vsh")),
                Mode.KEEP);
        assertEquals(List.of(Severity.UNUSED), report.findings().stream().map(Finding::severity).toList());
        assertTrue(report.findings().get(0).message().contains("minecraft namespace"));
        assertTrue(ShaderCheck.isShader("overlay_1_21_4/assets/minecraft/shaders/core/x.vsh"));
        assertTrue(!ShaderCheck.isShader("assets/minecraft/textures/block/stone.png"));
    }

    /**
     * As ItemsAdder's generated pack ships them: an old shader at the root, a 1.21.6+ one in an
     * overlay for formats 63 and up, and one for 26.1 clients. A 1.21.8 client (format 64) loads the
     * overlay's - the root's and the 26.1 one are not its, and not read.
     */
    @Test
    void onlyWhatThe1218ClientLoadsIsReadOverlaysIncluded() throws IOException {
        String root = "assets/minecraft/shaders/core/terrain.vsh";
        String modern = "overlay_1_21_6/assets/minecraft/shaders/core/terrain.vsh";
        String future = "overlay_26_1/assets/minecraft/shaders/core/terrain.vsh";
        Map<String, String> pack = Map.of(root, OLD_TRANSLUCENT, modern, terrain("terrain-like.vsh"),
                future, "void main() {}\n");
        List<ShaderCheck.Overlay> overlays = List.of(new ShaderCheck.Overlay("overlay_1_21_6", 63, 65535),
                new ShaderCheck.Overlay("overlay_26_1", 84, 65535));
        Report report = ShaderCheck.check(pack, Mode.DROP_BROKEN, overlays);
        assertEquals(List.of(), report.findings());
        assertEquals(1, report.checked());
        assertEquals(2, report.skipped());

        // Without the overlay for its format, the root's old shader is what it loads - and is dropped.
        Report bare = ShaderCheck.check(pack, Mode.DROP_BROKEN, List.of(new ShaderCheck.Overlay("overlay_26_1", 84, 65535)));
        assertEquals(Set.of(), report.drop());
        assertEquals(Set.of(root), bare.drop(), "its other half is not in the pack: nothing more to leave out");

        // pack.mcmeta's three ways of writing formats.
        assertEquals(new ShaderCheck.Overlay("a", 63, 64), ShaderCheck.Overlay.of(com.google.gson.JsonParser.parseString(
                "{\"directory\":\"a\",\"formats\":[63,64]}").getAsJsonObject()));
        assertEquals(new ShaderCheck.Overlay("b", 63, 65535), ShaderCheck.Overlay.of(com.google.gson.JsonParser.parseString(
                "{\"directory\":\"b\",\"formats\":{\"min_inclusive\":63,\"max_inclusive\":65535}}").getAsJsonObject()));
        assertEquals(new ShaderCheck.Overlay("c", 64, 64), ShaderCheck.Overlay.of(com.google.gson.JsonParser.parseString(
                "{\"directory\":\"c\",\"formats\":64}").getAsJsonObject()));
        assertEquals(64, ShaderCheck.clientFormat());
    }

    /** Dropping a broken overlay file lets the root's show through - and that one is read in turn. */
    @Test
    void droppingABrokenFileReadsTheOneUnderIt() throws IOException {
        String root = "assets/minecraft/shaders/core/terrain.vsh";
        String overlay = "fix/assets/minecraft/shaders/core/terrain.vsh";
        Report report = ShaderCheck.check(Map.of(root, terrain("terrain-like.vsh"), overlay, OLD_TRANSLUCENT),
                Mode.DROP_BROKEN, List.of(new ShaderCheck.Overlay("fix", 63, 64)));
        assertEquals(Set.of(overlay), report.drop());
        assertEquals(List.of(Severity.BROKEN), report.findings().stream().map(Finding::severity).toList(),
                "the root's, read next, is fine");
    }
}
