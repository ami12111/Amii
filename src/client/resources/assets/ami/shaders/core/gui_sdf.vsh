#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;        // local position in physical pixels, relative to the shape centre
in ivec2 UV1;       // packed half size * 4
in ivec2 UV2;       // packed (radius, stroke width) * 4
in float LineWidth; // shadow blur in pixels (0 = not a shadow)

out vec4 vColor;
out vec2 vLocal;
out vec2 vHalfSize;
out vec2 vRadiusStroke;
out float vBlur;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vColor = Color;
    vLocal = UV0;
    vHalfSize = vec2(UV1) * 0.25;
    vRadiusStroke = vec2(UV2) * 0.25;
    vBlur = LineWidth;
}
