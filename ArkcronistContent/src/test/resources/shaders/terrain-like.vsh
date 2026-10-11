#version 150

// Written for these tests: the inputs, outputs, imports and uniform blocks a 1.21.8 terrain
// vertex shader has, with a body of its own.

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec3 world = Position + ModelOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(world, 1.0);
    sphericalVertexDistance = fog_spherical_distance(world);
    cylindricalVertexDistance = fog_cylindrical_distance(world);
    vertexColor = Color * texelFetch(Sampler2, clamp(UV2 >> 4, ivec2(0), ivec2(15)), 0);
    texCoord0 = UV0;
}
