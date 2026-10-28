#version 330

// ---------------------------------------------------------------------------
// HitParticle fragment shader.
//
// Every particle is a single camera-facing quad and its whole look is
// procedural, no textures. Per-quad parameters arrive through the standard
// vertex attributes:
//   vertexColor.rgb  - tint colour
//   vertexColor.a    - particle progress t in [0..1]
//   texCoord0        - local quad coords in [-1..1], plus a per-quad offset:
//                        +4 * seed  on X  (random id of the particle, 0..63)
//                        +4 * kind  on Y  (0 = spark streak,
//                                          1 = crystal shard,
//                                          2 = glowing ember,
//                                          3 = energy wisp,
//                                          4 = impact flash,
//                                          5 = shockwave ring,
//                                          6 = critical star)
// Sparks are stretched along their velocity by the CPU: local +X is the head
// of the comet, local -X is its tail. Everything else is a plain square.
// Blending is additive, so the shader outputs light only.
// ---------------------------------------------------------------------------

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

#define PI  3.14159265359
#define TAU 6.28318530718

// ------------------------------------------------------------------ hashing
float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float amp = 0.5;
    mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 4; i++) {
        v += amp * vnoise(p);
        p = m * p;
        amp *= 0.5;
    }
    return v;
}

// ------------------------------------------------------------------ helpers
mat2 rot2(float a) {
    float c = cos(a);
    float s = sin(a);
    return mat2(c, -s, s, c);
}

// rotate an RGB colour around the grey axis
vec3 hueShift(vec3 col, float a) {
    const vec3 k = vec3(0.57735026919);
    float c = cos(a);
    return col * c + cross(k, col) * sin(a) + k * dot(k, col) * (1.0 - c);
}

float easeOutCubic(float x) {
    x = clamp(x, 0.0, 1.0);
    float y = 1.0 - x;
    return 1.0 - y * y * y;
}

// crisp core + soft halo, d = distance to the line, w = half width
float stroke(float d, float w) {
    float core = 1.0 - smoothstep(w * 0.6, w, d);
    float halo = exp(-d / (w * 5.0)) * 0.30;
    return core + halo;
}

// heat 1 = white-hot, ~0.5 = the tint itself, 0 = cooled ember
vec3 heatColor(vec3 tint, float heat) {
    vec3 hot = mix(tint, vec3(1.0), 0.82) * 1.7;
    vec3 mid = tint * 1.45;
    vec3 deep = hueShift(tint, -0.5) * 0.8;
    heat = clamp(heat, 0.0, 1.0);
    vec3 c = mix(deep, mid, smoothstep(0.0, 0.55, heat));
    return mix(c, hot, smoothstep(0.55, 1.0, heat));
}

// signed distance to a rhombus with half extents b (iq)
float sdRhombus(vec2 p, vec2 b) {
    p = abs(p);
    float h = clamp(((b.x - 2.0 * p.x) * b.x - (b.y - 2.0 * p.y) * b.y) / dot(b, b), -1.0, 1.0);
    float d = length(p - 0.5 * b * vec2(1.0 - h, 1.0 + h));
    return d * sign(p.x * b.y + p.y * b.x - b.x * b.y);
}

// ------------------------------------------------------------ kind 0: spark
// a comet: hair-thin shimmering tail, white-hot round head, glitter on the trail
vec3 sparkParticle(vec2 p, float t, float seed, vec3 tint) {
    float u = p.x * 0.5 + 0.5;          // 0 = tail tip, 1 = head tip
    float ay = abs(p.y);

    const float HEAD = 0.80;
    float w = mix(0.030, 0.230, smoothstep(0.0, HEAD, u));
    float capX = clamp((u - HEAD) / (1.0 - HEAD), 0.0, 1.0);
    w *= sqrt(max(1.0 - capX * capX, 0.0));
    w = max(w, 0.006);

    float across = ay / w;
    float body = exp(-across * across * 2.4);
    float along = pow(u, 1.25);

    // electric shimmer running down the tail, plus a fast flicker
    float n = vnoise(vec2(u * 10.0 - t * 22.0, seed * 3.7));
    float shimmer = mix(0.50, 1.0, n);
    float flick = 0.82 + 0.18 * sin(t * 70.0 + seed * 9.0);

    // hottest at the head, cooling down with age
    float heat = clamp((1.0 - t) * (0.30 + 0.80 * u), 0.0, 1.0);
    vec3 col = heatColor(tint, heat) * body * along * shimmer * flick * 1.25;

    // soft aura around the streak; gaussian, so it is gone long before the quad border
    float aw = 0.10 + 0.90 * w;
    float aura = exp(-(ay / aw) * (ay / aw)) * pow(u, 1.8) * 0.22 * (1.0 - capX * 0.5);
    col += tint * 1.2 * aura;

    // white-hot head bead
    vec2 hp = vec2((u - 0.86) / 0.10, p.y / 0.26);
    col += mix(tint, vec3(1.0), 0.9) * exp(-dot(hp, hp)) * 1.35 * flick;

    // glittering beads shed along the trail, each one twinkling on its own
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float bu = 0.20 + 0.58 * hash11(seed * 7.1 + fi * 3.3);
        float by = (hash11(seed * 5.3 + fi * 1.9) - 0.5) * 0.30;
        vec2 bp = vec2((u - bu) / 0.030, (p.y - by) / 0.11);
        float twk = 0.45 + 0.55 * sin(t * 40.0 + fi * 2.1 + seed * 5.0);
        col += mix(tint, vec3(1.0), 0.8) * exp(-dot(bp, bp)) * max(twk, 0.0) * 0.95;
    }

    float fade = smoothstep(0.0, 0.04, t) * (1.0 - smoothstep(0.45, 1.0, t));
    return col * fade;
}

// ------------------------------------------------------------ kind 1: shard
// a faceted crystal that tumbles, glints and finally burns away into sparks
vec3 shardParticle(vec2 p0, float t, float seed, vec3 tint) {
    // fake 3D tumble: the crystal turns edge-on now and then
    float phase = seed * 2.399 + t * (5.0 + 9.0 * hash11(seed * 3.1));
    float squash = mix(0.30, 1.0, abs(cos(phase)));
    vec2 p = vec2(p0.x / squash, p0.y);

    // a slightly different silhouette for every shard
    float wv = mix(0.40, 0.68, hash11(seed * 1.7));
    float shear = (hash11(seed * 2.9) - 0.5) * 0.5;
    vec2 q = vec2(p.x - shear * p.y, p.y);
    float d = sdRhombus(q, vec2(wv, 0.86));
    float cutA = hash11(seed * 4.3) * TAU;
    d = max(d, dot(q, vec2(cos(cutA), sin(cutA))) - 0.50);

    float inside = 1.0 - smoothstep(-0.012, 0.012, d);

    // four facets split by two ridge lines, each with its own brightness
    float rx = q.x - 0.18 * q.y;
    float ry = q.y - 0.10;
    float fid = step(0.0, rx) + 2.0 * step(0.0, ry);
    float facet = 0.28 + 0.72 * hash11(fid * 5.17 + seed * 3.3);
    vec2 lightDir = normalize(vec2(-0.55, 0.85));
    facet *= 0.70 + 0.30 * dot(normalize(q + 1e-3), lightDir);
    facet += 0.30 * (1.0 - smoothstep(0.0, 0.9, length(q)));
    float refr = 0.5 + 0.5 * sin((q.x * 0.6 + q.y) * 18.0 + seed * 5.0);
    facet *= 0.80 + 0.20 * refr;

    float ridge = stroke(abs(rx), 0.014) + stroke(abs(ry), 0.012);
    float rim = exp(-abs(d) * 26.0);

    // a specular glint that sweeps across the crystal
    float sweep = fract(t * 1.7 + hash11(seed));
    float glint = (1.0 - smoothstep(0.0, 0.09, abs(dot(q, vec2(0.72, 0.69)) - (sweep * 2.6 - 1.3)))) * inside;

    vec3 base = tint * 1.35;
    vec3 hot = mix(tint, vec3(1.0), 0.78) * 1.6;
    vec3 col = base * facet * inside * 0.95
             + hot * (ridge * inside * 0.45 + rim * 0.55 + glint * 1.2);

    // dissolve into fine grains with a glowing burn edge
    float n = 0.6 * vnoise(q * 11.0 + seed) + 0.4 * vnoise(q * 23.0 - seed);
    float thr = smoothstep(0.50, 1.0, t) * 1.20 - 0.10;
    float vis = smoothstep(thr, thr + 0.05, n);
    float burn = smoothstep(thr - 0.03, thr + 0.005, n)
               * (1.0 - smoothstep(thr + 0.005, thr + 0.07, n));
    col = col * vis + hueShift(hot, -0.45) * burn * inside * 1.9;

    float edge = 1.0 - smoothstep(0.86, 1.0, max(abs(p0.x), abs(p0.y)));
    return col * edge * smoothstep(0.0, 0.05, t);
}

// ------------------------------------------------------------ kind 2: ember
// a glowing mote with a twinkling lens flare
vec3 emberParticle(vec2 p, float t, float seed, vec3 tint) {
    float r = length(p);
    float tw = 0.65 + 0.35 * sin(t * 38.0 + seed * 11.0);

    float core = exp(-r * r * 55.0);
    float halo = exp(-r * 6.5) * 0.45;

    vec2 q = rot2(seed * 1.3) * p;
    float f1 = exp(-abs(q.x) * 6.0) * exp(-abs(q.y) * 48.0);
    float f2 = exp(-abs(q.y) * 6.0) * exp(-abs(q.x) * 48.0);
    vec2 q2 = rot2(PI * 0.25) * q;
    float f3 = exp(-abs(q2.x) * 9.0) * exp(-abs(q2.y) * 60.0)
             + exp(-abs(q2.y) * 9.0) * exp(-abs(q2.x) * 60.0);

    vec3 hot = mix(tint, vec3(1.0), 0.8) * 1.5;
    vec3 col = hot * core * 1.4
             + tint * 1.4 * halo * 0.8
             + mix(tint, vec3(1.0), 0.5) * ((f1 + f2) * 1.10 + f3 * 0.40) * tw;

    float fade = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.50, 1.0, t));
    return col * fade;
}

// ------------------------------------------------------------- kind 3: wisp
// a puff of domain-warped energy smoke that dissolves with a hot edge
vec3 wispParticle(vec2 p, float t, float seed, vec3 tint) {
    float r = length(p);
    vec2 q = p * 1.9 + vec2(seed * 7.3, seed * 3.1);
    float n1 = fbm(q + vec2(t * 0.9, -t * 0.6));
    float n2 = fbm(q * 1.7 + n1 * 2.2 - vec2(t * 0.5, 0.0));

    float dens = smoothstep(0.28, 0.85, n2) * (1.0 - smoothstep(0.25, 0.95, r));

    float thr = smoothstep(0.25, 1.0, t) * 1.1 - 0.1;
    float vis = smoothstep(thr, thr + 0.08, n2);
    float burn = smoothstep(thr - 0.03, thr + 0.01, n2)
               * (1.0 - smoothstep(thr + 0.01, thr + 0.09, n2));

    vec3 body = mix(tint * 0.55, hueShift(tint, -0.6) * 0.9, n1);
    vec3 col = body * dens * vis * 0.9
             + mix(tint, vec3(1.0), 0.5) * burn * dens * 1.2;
    col += tint * exp(-r * r * 6.0) * 0.18 * (1.0 - t);

    return col * smoothstep(0.0, 0.10, t);
}

// ------------------------------------------------------------- kind 4: flash
// the blinding moment of impact: bloom, anamorphic streak, spiky rays
vec3 flashParticle(vec2 p, float t, float seed, vec3 tint) {
    float r = length(p);
    float a = atan(p.y, p.x);
    float e = easeOutCubic(t);
    float fadeFast = pow(1.0 - t, 1.6);
    vec3 hot = mix(tint, vec3(1.0), 0.8) * 1.5;

    float core = exp(-r * r * mix(30.0, 9.0, e)) * 1.9;

    // horizontal lens streak with a fainter vertical one
    float sx = exp(-abs(p.y) * 38.0) * exp(-abs(p.x) * 2.8);
    float sy = exp(-abs(p.x) * 60.0) * exp(-abs(p.y) * 5.5) * 0.5;

    // spiky rays
    float rayA = pow(0.5 + 0.5 * sin(a * 11.0 + seed * 6.1), 9.0);
    float rayB = pow(0.5 + 0.5 * sin(a * 23.0 - seed * 3.7), 18.0) * 0.6;
    float rayLen = 0.25 + 0.70 * e;
    float rays = (rayA + rayB) * smoothstep(0.02, 0.10, r)
               * (1.0 - smoothstep(rayLen * 0.6, rayLen, r));

    // a thin bright ripple racing outwards
    float ripple = stroke(abs(r - e * 0.85), 0.012) * (1.0 - t);

    vec3 col = hot * core
             + tint * 1.5 * (sx + sy) * 1.1
             + mix(tint, vec3(1.0), 0.4) * rays * 1.4
             + tint * 1.4 * ripple * 0.8
             + tint * exp(-r * r * 5.0) * 0.35;
    return col * fadeFast;
}

// -------------------------------------------------------------- kind 5: ring
// prismatic shockwave with energy streaks behind its front
vec3 ringParticle(vec2 p, float t, float seed, vec3 tint) {
    float r = length(p);
    float a = atan(p.y, p.x);
    float e = easeOutCubic(t);
    float rw = 0.10 + e * 0.82;

    vec2 dir = vec2(cos(a), sin(a));
    float wob = (fbm(dir * 2.4 + seed * 3.1 + t * 0.8) - 0.5) * 0.10 * (1.0 - t);
    float w = mix(0.070, 0.014, e);

    // split the front per colour channel for a prismatic edge
    vec3 ring = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float off = (float(c) - 1.0) * 0.020 * (1.0 - e * 0.4);
        float x = (r - (rw + wob + off)) / w;
        float front = exp(-x * x);
        float tail = x < 0.0 ? 0.40 * exp(x * 0.40) : 0.0;
        ring[c] = front + tail;
    }
    vec3 col = ring * mix(vec3(1.0), tint * 1.7, 0.60);

    // hair-thin speed lines trailing behind the front, every one a different length
    float cells = 72.0;
    float ca = (a / TAU + 0.5) * cells;
    float lid = floor(ca);
    float lf = fract(ca) - 0.5;
    float lh = hash11(lid + seed * 19.0);
    float lh2 = hash11(lid * 1.7 + seed * 7.0);
    float across = lf * (TAU / cells) * r / 0.0055;
    float lineW = exp(-across * across);
    float lineLen = 0.08 + 0.30 * lh2;
    float band = smoothstep(rw - lineLen, rw - 0.01, r) * (1.0 - smoothstep(rw - 0.01, rw + 0.01, r));
    col += tint * 1.7 * step(0.45, lh) * lineW * band * (1.0 - t);

    // a smaller echo ring
    col += tint * 0.7 * stroke(abs(r - rw * 0.62), 0.006) * (1.0 - t) * smoothstep(0.05, 0.20, t);

    return col * (1.0 - smoothstep(0.30, 1.0, t));
}

// -------------------------------------------------------------- kind 6: star
// critical hit: a spinning many-pointed star inside a dashed halo
vec3 starParticle(vec2 p, float t, float seed, vec3 tint) {
    float r = length(p);
    float a = atan(p.y, p.x);
    float e = easeOutCubic(t);
    float spin = seed * 1.7 + e * 1.1;
    vec3 hot = mix(tint, vec3(1.0), 0.75) * 1.6;

    // slender cross-shaped lens spikes: constant hair width, brightness tapering
    // with length; the long cross is rotating, the short one trails behind it
    float len = (0.40 + 0.55 * e) * (1.0 - 0.35 * t);
    vec2 q = rot2(spin) * p;
    vec2 q2 = rot2(-spin * 0.6 + PI * 0.25) * p;
    float lenLong = 1.0 - smoothstep(0.0, len, abs(q.x));
    float lenLong2 = 1.0 - smoothstep(0.0, len, abs(q.y));
    float spikes = exp(-abs(q.y) * 70.0) * lenLong * lenLong
                 + exp(-abs(q.x) * 70.0) * lenLong2 * lenLong2;
    float lenShort = 1.0 - smoothstep(0.0, len * 0.62, abs(q2.x));
    float lenShort2 = 1.0 - smoothstep(0.0, len * 0.62, abs(q2.y));
    float spikes2 = exp(-abs(q2.y) * 80.0) * lenShort * lenShort
                  + exp(-abs(q2.x) * 80.0) * lenShort2 * lenShort2;
    vec3 col = hot * (spikes * 1.7 + spikes2 * 0.9);

    // soft glow around the cross
    col += tint * exp(-r * r * 9.0) * 0.55;

    // rotating dashed halo
    float ringR = 0.56 + 0.30 * e;
    float dp = fract((a - spin * 1.8) / TAU * 18.0);
    float dashes = smoothstep(0.35, 0.55, dp) * (1.0 - smoothstep(0.75, 0.95, dp));
    col += tint * 1.6 * stroke(abs(r - ringR), 0.0075) * dashes * (1.0 - t);

    // a breathing diamond
    vec2 dq = rot2(spin * 0.5) * p;
    float dia = abs(dq.x) + abs(dq.y);
    col += hueShift(tint, -0.5) * 1.5 * stroke(abs(dia - (0.20 + 0.22 * e)), 0.009) * (1.0 - t);

    // white-hot core
    col += hot * exp(-r * r * 40.0) * 1.5;

    return col * smoothstep(0.0, 0.03, t) * (1.0 - smoothstep(0.50, 1.0, t));
}

// -------------------------------------------------------------------- main
void main() {
    vec2 cell = floor((texCoord0 + 2.0) / 4.0);
    vec2 p = texCoord0 - cell * 4.0;
    float seed = cell.x;
    int kind = int(cell.y + 0.5);

    float t = clamp(vertexColor.a, 0.0, 1.0);
    vec3 tint = vertexColor.rgb;

    vec3 col;
    float edge = 1.0 - smoothstep(0.90, 1.0, length(p));
    if (kind == 0) {
        col = sparkParticle(p, t, seed, tint);
        edge = (1.0 - smoothstep(0.86, 1.0, abs(p.x))) * (1.0 - smoothstep(0.80, 1.0, abs(p.y)));
    } else if (kind == 1) {
        col = shardParticle(p, t, seed, tint);
        edge = 1.0;
    } else if (kind == 2) {
        col = emberParticle(p, t, seed, tint);
    } else if (kind == 3) {
        col = wispParticle(p, t, seed, tint);
    } else if (kind == 4) {
        col = flashParticle(p, t, seed, tint);
    } else if (kind == 5) {
        col = ringParticle(p, t, seed, tint);
    } else {
        col = starParticle(p, t, seed, tint);
    }

    col = 1.0 - exp(-col * 1.15);
    col *= edge;

    fragColor = vec4(col, 1.0);
}
