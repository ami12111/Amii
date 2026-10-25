#version 330

in vec4 vColor;
in vec2 vLocal;
in vec2 vHalfSize;
in vec2 vRadiusStroke;
in float vBlur;

out vec4 fragColor;

float sdRoundRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    float radius = vRadiusStroke.x;
    float stroke = vRadiusStroke.y;
    float d = sdRoundRect(vLocal, vHalfSize, radius);
    float aa = max(fwidth(d), 0.0001);

    float alpha;
    if (vBlur > 0.0) {
        alpha = 1.0 - smoothstep(-vBlur, vBlur, d);
    } else if (stroke > 0.0) {
        alpha = 1.0 - smoothstep(stroke - aa, stroke + aa, abs(d + stroke * 0.5));
    } else {
        alpha = 1.0 - smoothstep(-aa, aa, d);
    }

    fragColor = vec4(vColor.rgb, vColor.a * alpha);
}
