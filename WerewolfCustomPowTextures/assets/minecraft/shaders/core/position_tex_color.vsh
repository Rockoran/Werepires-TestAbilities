#version 330

// Can't moj_import in things used during startup, when resource packs don't exist.
// This is a copy of dynamicimports.glsl and projection.glsl
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
    float LineWidth;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

uniform sampler2D Sampler0;

in vec3 Position;
in vec2 UV0;
in vec4 Color;

out vec2 texCoord0;
out vec4 vertexColor;

vec2[] corners = vec2[](vec2(0, 0), vec2(0, 1), vec2(1, 1), vec2(1, 0));

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    texCoord0 = UV0;
    vertexColor = Color;

    vec3 pos = Position;

    vec4 identifier = texture(Sampler0, vec2(0));

    if (ivec4(round(identifier * 255)) == ivec4(1, 1, 1, 2)) {
        vec2 corner = corners[gl_VertexID % 4];

        pos.x += (corner.x - 0.5) * 176;
        pos.y += (corner.y - 0.5) * 166;

        gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);
    }
}
