#version 330

// ---------------------------------------------------------------------------
// JumpCircles fragment shader.
//
// Everything is procedural, no textures. Per-quad parameters arrive through
// the standard vertex attributes:
//   vertexColor.rgb  - tint colour
//   vertexColor.a    - effect progress t in [0..1]
//   texCoord0        - local quad coords in [-1..1], plus a per-quad offset:
//                        +4 * seed  on X  (seed 0..15)
//                        +4 * kind  on Y  (0 = takeoff rune circle,
//                                          1 = landing shockwave,
//                                          2 = rising halo ring,
//                                          3 = takeoff spiral,
//                                          4 = landing starburst,
//                                          5 = landing rune fracture)
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

vec2 hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
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

float segDist(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

// crisp core + soft halo, d = distance to the line, w = half width
float stroke(float d, float w) {
    float core = 1.0 - smoothstep(w * 0.6, w, d);
    float halo = exp(-d / (w * 5.0)) * 0.30;
    return core + halo;
}

// distance to the outline of a regular n-gon (circumradius R)
float polyEdge(vec2 p, float n, float R) {
    float an = TAU / n;
    float a = atan(p.y, p.x);
    float b = mod(a, an) - 0.5 * an;
    return abs(length(p) * cos(b) - R * cos(0.5 * an));
}

// angular "draw-on" reveal, ang01 in [0..1)
float reveal(float ang01, float prog) {
    return 1.0 - smoothstep(prog - 0.03, prog, ang01);
}

// ------------------------------------------------------------------- glyphs
// tiny procedural rune built out of up to six strokes, g in band units
float glyphDist(vec2 g, float id) {
    float d = 10.0;
    float h1 = hash11(id * 1.37 + 0.11);
    float h2 = hash11(id * 2.71 + 0.23);
    float h3 = hash11(id * 3.91 + 0.37);
    float h4 = hash11(id * 5.13 + 0.51);
    float h5 = hash11(id * 6.77 + 0.73);
    float h6 = hash11(id * 8.29 + 0.97);
    if (h1 > 0.2)  d = min(d, segDist(g, vec2( 0.00, -0.40), vec2( 0.00, 0.40)));
    if (h2 > 0.5)  d = min(d, segDist(g, vec2(-0.26,  0.40), vec2( 0.26, 0.40)));
    if (h3 > 0.5)  d = min(d, segDist(g, vec2(-0.26, -0.40), vec2( 0.00, 0.02)));
    if (h4 > 0.5)  d = min(d, segDist(g, vec2( 0.26, -0.40), vec2( 0.00, 0.02)));
    if (h5 > 0.65) d = min(d, abs(length(g - vec2(0.0, 0.02)) - 0.15));
    if (h6 > 0.45) d = min(d, length(g - vec2(0.0, -0.47)));
    return d;
}

float glyphRing(float r, float a, float r0, float bandW, float n, float rotA, float seed) {
    float cf = (a + rotA) / TAU * n;
    float cellId = floor(cf);
    float la = fract(cf) - 0.5;
    float arc = r * TAU / n;
    vec2 g = vec2(la * arc / bandW, (r - r0) / bandW);
    float id = mod(cellId, n) + 1.0 + seed * 31.0;
    float d = glyphDist(g, id) * bandW;
    return stroke(d, 0.0042);
}

// ------------------------------------------------------------- particles
float sparks(vec2 p, float t, float seed) {
    float acc = 0.0;
    for (int i = 0; i < 26; i++) {
        float fi = float(i) + seed * 29.0;
        float ang = hash11(fi * 1.713) * TAU;
        float spd = 0.45 + 0.50 * hash11(fi * 2.937);
        float life = 0.50 + 0.50 * hash11(fi * 4.119);
        float pt = t / life;
        if (pt >= 1.0) {
            continue;
        }
        float rad = spd * easeOutCubic(pt);
        float swirl = (hash11(fi * 5.301) - 0.5) * 2.4 * pt;
        vec2 pos = vec2(cos(ang + swirl), sin(ang + swirl)) * rad;
        float d = length(p - pos);
        float sz = mix(0.020, 0.004, pt);
        float tw = 0.65 + 0.35 * sin(t * 50.0 + fi * 3.7);
        float core = 1.0 - smoothstep(0.0, sz, d);
        float glow = exp(-d / (sz * 4.0)) * 0.18;
        acc += (core * tw + glow) * (1.0 - pt);
    }
    return acc;
}

// ----------------------------------------------------------------- cracks
// returns ~0 on voronoi cell borders
float cracks(vec2 p, float seed) {
    vec2 q = p * 4.2 + seed * 7.3;
    vec2 ip = floor(q);
    vec2 fp = fract(q);
    float f1 = 8.0;
    float f2 = 8.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 g = vec2(float(i), float(j));
            vec2 o = hash22(ip + g);
            vec2 rr = g + o - fp;
            float d = dot(rr, rr);
            if (d < f1) {
                f2 = f1;
                f1 = d;
            } else if (d < f2) {
                f2 = d;
            }
        }
    }
    return sqrt(f2) - sqrt(f1);
}

// -------------------------------------------------------------- shockwave
// expanding prismatic ring with a trailing ripple field
vec3 shockwave(vec2 p, float r, float a, float t, float seed, vec3 tint, float strength) {
    float e = easeOutCubic(t);
    float rw = 0.06 + e * 0.90;

    vec2 dir = vec2(cos(a), sin(a));
    float wob = (fbm(dir * 2.2 + seed * 3.1 + t * 0.6) - 0.5) * 0.07 * (1.0 - t);
    float w = mix(0.060, 0.012, e);

    vec3 ring = vec3(0.0);
    for (int c = 0; c < 3; c++) {
        float off = (float(c) - 1.0) * 0.022 * (1.0 - e * 0.5);
        float x = (r - (rw + wob + off)) / w;
        float front = exp(-x * x);
        float tail = x < 0.0 ? 0.35 * exp(x * 0.45) : 0.0;
        ring[c] = front + tail;
    }

    vec3 col = ring * mix(vec3(1.0), tint * 1.6, 0.55) * strength * (1.0 - t * 0.6);

    float behind = (1.0 - smoothstep(rw - 0.5, rw, r)) * smoothstep(0.0, 0.15, r);
    float rip = 0.5 + 0.5 * sin(r * 70.0 - e * 55.0);
    col += tint * behind * rip * 0.10 * (1.0 - t) * strength;
    return col;
}

// ------------------------------------------------------------ kind 0: takeoff
vec3 takeoffCircle(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 cMain = tint * 1.5;
    vec3 cAlt  = hueShift(tint, 2.1) * 1.4;
    vec3 cHot  = mix(tint, vec3(1.0), 0.65) * 1.6;

    float ang01 = fract(a / TAU + 0.5);
    float tIn   = clamp(t / 0.28, 0.0, 1.0);
    float prog  = easeOutCubic(tIn) * 1.06;
    float spin  = easeOutCubic(t) * 2.2;
    float tOut  = smoothstep(0.55, 1.0, t);

    vec3 pat = vec3(0.0);

    // outer double ring
    pat += cMain * stroke(abs(r - 0.900), 0.0065) * reveal(ang01, prog);
    pat += cMain * 0.6 * stroke(abs(r - 0.865), 0.0035) * reveal(ang01, prog * 0.95);

    // tick marks, every fifth one is longer
    float tf = (a - spin * 0.6) / TAU * 90.0;
    float tId = floor(tf);
    float tl = abs(fract(tf) - 0.5);
    float longTick = step(mod(tId, 5.0), 0.5);
    float tickIn = mix(0.800, 0.775, longTick);
    float tickMask = (1.0 - smoothstep(0.07, 0.13, tl))
                   * smoothstep(tickIn - 0.004, tickIn, r)
                   * (1.0 - smoothstep(0.835, 0.842, r));
    pat += cMain * 0.9 * tickMask * reveal(ang01, prog * 0.9);

    // dashed ring
    float dashPhase = fract((a + spin * 0.8) / TAU * 48.0);
    float dash = smoothstep(0.0, 0.08, dashPhase) * (1.0 - smoothstep(0.5, 0.58, dashPhase));
    pat += cAlt * stroke(abs(r - 0.745), 0.004) * dash * reveal(ang01, prog * 0.9);

    // rune band
    pat += cMain * stroke(abs(r - 0.705), 0.0045) * reveal(ang01, prog);
    pat += cMain * stroke(abs(r - 0.572), 0.0045) * reveal(ang01, prog * 0.9);
    float bandMask = smoothstep(0.575, 0.585, r) * (1.0 - smoothstep(0.695, 0.705, r));
    pat += cAlt * 1.2 * glyphRing(r, a, 0.638, 0.126, 26.0, spin * 0.5, seed)
         * bandMask * reveal(ang01, prog * 0.85);

    // counter-rotating triangles (hexagram)
    vec2 p1 = rot2(spin * 0.7) * p;
    vec2 p2 = rot2(-spin * 0.7 + PI / 3.0) * p;
    pat += cHot * 0.9 * stroke(polyEdge(p1, 3.0, 0.52), 0.0045) * reveal(ang01, prog * 0.8);
    pat += cHot * 0.9 * stroke(polyEdge(p2, 3.0, 0.52), 0.0045) * reveal(ang01, prog * 0.8);

    // spokes between inner ring and hexagram
    float seg = TAU / 6.0;
    float da = abs(fract((a + spin * 0.7) / seg + 0.5) - 0.5) * seg;
    float spokeMask = smoothstep(0.30, 0.31, r) * (1.0 - smoothstep(0.51, 0.52, r));
    pat += cMain * 0.7 * stroke(r * sin(da), 0.0035) * spokeMask * reveal(ang01, prog * 0.75);

    // inner ring, octagon and breathing star
    pat += cMain * stroke(abs(r - 0.300), 0.0045) * reveal(ang01, prog * 0.75);
    pat += cAlt * 0.8 * stroke(polyEdge(rot2(-spin) * p, 8.0, 0.265), 0.0035) * reveal(ang01, prog * 0.7);
    float starR = 0.17 + 0.05 * cos(8.0 * (a + spin * 1.3));
    pat += cHot * stroke(abs(r - starR), 0.004) * reveal(ang01, prog * 0.7);

    // faint filled disc so the circle reads as a light pool on the ground
    pat += tint * 0.06 * (1.0 - smoothstep(0.0, 0.92, r)) * smoothstep(0.0, 0.15, t);

    // dissolve with a glowing burn edge
    float n = clamp((fbm(p * 4.5 + seed * 5.0) - 0.15) / 0.7 - (r - 0.5) * 0.3, 0.0, 1.0);
    float thr = tOut * 1.3 - 0.15;
    float vis = smoothstep(thr, thr + 0.06, n);
    float burn = smoothstep(thr - 0.03, thr + 0.01, n)
               * (1.0 - smoothstep(thr + 0.01, thr + 0.10, n))
               * smoothstep(0.0, 0.05, tOut);

    vec3 col = pat * vis + cHot * burn * 1.3;

    // centre glow and light rays (fade on their own, not dissolved)
    float pulse = 0.85 + 0.15 * sin(t * 24.0);
    col += cHot * exp(-r * r * 18.0) * 0.9 * pulse * (1.0 - tOut);
    col += hueShift(tint, -0.5) * exp(-r * r * 4.0) * 0.25 * (1.0 - tOut);
    float rayN = vnoise(vec2(cos(a), sin(a)) * 3.0 + vec2(spin * 1.5, seed));
    col += cMain * pow(rayN, 3.0) * exp(-r * 2.6) * 0.7 * (1.0 - tOut);

    // shockwave and sparks on top
    col += shockwave(p, r, a, t, seed, tint, 0.85);
    col += cHot * sparks(p, t, seed) * 1.1;
    return col;
}

// ------------------------------------------------------------ kind 1: landing
vec3 landingBurst(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 cAlt = hueShift(tint, 2.1) * 1.4;
    vec3 cHot = mix(tint, vec3(1.0), 0.65) * 1.6;

    float e = easeOutCubic(t);
    float rw = 0.06 + e * 0.90;

    vec3 col = shockwave(p, r, a, t, seed, tint, 1.5);

    // energy cracks that stay behind the front
    float crack = 1.0 - smoothstep(0.0, 0.07, cracks(p, seed));
    float reach = 1.0 - smoothstep(rw * 0.85 - 0.25, rw * 0.85 + 0.05, r);
    float cm = smoothstep(0.35, 0.60, vnoise(p * 6.0 + seed));
    col += cHot * crack * reach * cm * (1.0 - smoothstep(0.35, 0.90, t)) * 1.5;

    // dust jets right behind the front
    vec2 dir = vec2(cos(a), sin(a));
    float jets = pow(vnoise(dir * 5.0 + vec2(seed * 2.1, 0.0)), 4.0);
    float band = smoothstep(rw - 0.45, rw, r) * (1.0 - smoothstep(rw, rw + 0.03, r));
    col += tint * jets * band * 1.4 * (1.0 - t);

    // core flash and a smaller echo ring
    col += cHot * exp(-r * r * 10.0) * (1.0 - smoothstep(0.0, 0.35, t));
    float e2 = easeOutCubic(t * 1.3);
    col += cAlt * 0.7 * stroke(abs(r - e2 * 0.55), 0.006) * (1.0 - t) * smoothstep(0.0, 0.1, t);

    col += cHot * sparks(p, t, seed) * 1.0;
    return col;
}

// --------------------------------------------------------- kind 2: halo ring
vec3 haloRing(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 c = hueShift(tint, seed * 0.5 - 0.3) * 1.5;
    float rr = 0.78 - 0.10 * t;
    float d = abs(r - rr);
    float body = exp(-(d / 0.045) * (d / 0.045)) * 0.55 + stroke(d, 0.008);
    float lobes = 0.55 + 0.45 * sin(a * 9.0 + t * 8.0 * (1.0 + seed) + seed * 2.0);
    float sparkle = vnoise(vec2(cos(a), sin(a)) * 6.0 + vec2(t * 8.0, seed * 3.0));
    return c * body * lobes * (0.6 + 0.8 * sparkle);
}

// ------------------------------------------------- kind 3: takeoff spiral
vec3 takeoffSpiral(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 cMain = tint * 1.5;
    vec3 cAlt = hueShift(tint, 2.1) * 1.4;
    vec3 cHot = mix(tint, vec3(1.0), 0.65) * 1.7;

    float e = easeOutCubic(t);
    float spin = e * 3.4 + seed * 0.9;
    float tOut = smoothstep(0.58, 1.0, t);
    float ang01 = fract(a / TAU + 0.5);
    float prog = easeOutCubic(clamp(t / 0.26, 0.0, 1.0)) * 1.06;

    vec3 pat = vec3(0.0);

    // three logarithmic spiral arms
    float arms = pow(0.5 + 0.5 * sin(a * 3.0 - log(max(r, 0.03)) * 4.2 + spin * 2.0), 12.0);
    float armMask = smoothstep(0.10, 0.20, r) * (1.0 - smoothstep(0.78, 0.92, r));
    pat += cMain * arms * armMask * 0.95 * reveal(ang01, prog);

    // outer and inner rings
    float outer = 0.86 + 0.02 * sin(t * 9.0);
    pat += cMain * stroke(abs(r - outer), 0.006) * reveal(ang01, prog);
    pat += cAlt * stroke(abs(r - 0.34), 0.005) * reveal(ang01, prog * 0.85);

    // rune band between the rings
    float bandMask = smoothstep(0.52, 0.53, r) * (1.0 - smoothstep(0.62, 0.63, r));
    pat += cAlt * 1.3 * glyphRing(r, a, 0.575, 0.10, 20.0, -spin * 0.6, seed) * bandMask
         * reveal(ang01, prog * 0.9);

    // gear teeth on the outer ring
    float teeth = step(0.72, fract((a + spin * 0.9) / TAU * 36.0));
    float toothMask = smoothstep(0.83, 0.86, r) * (1.0 - smoothstep(0.90, 0.905, r));
    pat += cHot * 0.8 * teeth * toothMask * reveal(ang01, prog);

    // dissolve with a glowing burn edge
    float n = clamp((fbm(p * 4.5 + seed * 5.0) - 0.15) / 0.7 - (r - 0.5) * 0.3, 0.0, 1.0);
    float thr = tOut * 1.3 - 0.15;
    float vis = smoothstep(thr, thr + 0.06, n);
    float burn = smoothstep(thr - 0.03, thr + 0.01, n)
               * (1.0 - smoothstep(thr + 0.01, thr + 0.10, n))
               * smoothstep(0.0, 0.05, tOut);

    vec3 col = pat * vis + cHot * burn * 1.2;

    float pulse = 0.85 + 0.15 * sin(t * 22.0);
    col += cHot * exp(-r * r * 20.0) * 0.85 * pulse * (1.0 - tOut);
    col += cAlt * sparks(p, t, seed) * 0.9;
    col += shockwave(p, r, a, t, seed, tint, 0.45);
    return col;
}

// ------------------------------------------------ kind 4: landing starburst
vec3 landingStar(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 cMain = tint * 1.6;
    vec3 cAlt = hueShift(tint, 2.1) * 1.4;
    vec3 cHot = mix(tint, vec3(1.0), 0.62) * 1.8;

    float e = easeOutCubic(t);
    vec3 col = vec3(0.0);

    // twelve radial beams stretching outwards
    float rays = pow(0.5 + 0.5 * sin(a * 12.0 + seed * 1.7), 8.0);
    float rayLen = 0.35 + e * 0.62;
    float ray = rays * smoothstep(0.03, 0.16, r) * (1.0 - smoothstep(rayLen * 0.72, rayLen, r));
    col += cHot * ray * 1.5 * (1.0 - t * 0.6);

    // counter-rotating spoke layer
    float spokes = pow(0.5 + 0.5 * sin(a * 6.0 - e * 3.0 + seed), 16.0);
    col += cAlt * spokes * smoothstep(0.10, 0.25, r)
         * (1.0 - smoothstep(0.35 + e * 0.5, 0.42 + e * 0.5, r)) * 1.1;

    // expanding ring and an echo
    float rw = 0.10 + e * 0.82;
    col += cMain * stroke(abs(r - rw), 0.012) * (1.0 - t);
    col += cAlt * 0.7 * stroke(abs(r - rw * 0.62), 0.005) * (1.0 - t);

    // centre flash
    col += cHot * exp(-r * r * 16.0) * (1.0 - smoothstep(0.0, 0.38, t)) * 1.6;

    // dust band behind the front and sparks
    float band = smoothstep(rw - 0.35, rw, r) * (1.0 - smoothstep(rw, rw + 0.03, r));
    col += tint * band * 0.5 * (1.0 - t);
    col += cAlt * sparks(p, t, seed) * 1.15;
    return col;
}

// ------------------------------------------- kind 5: landing rune fracture
vec3 landingRunes(vec2 p, float r, float a, float t, float seed, vec3 tint) {
    vec3 cMain = tint * 1.6;
    vec3 cAlt = hueShift(tint, 2.1) * 1.5;
    vec3 cHot = mix(tint, vec3(1.0), 0.7) * 1.7;

    float e = easeOutCubic(t);
    vec3 col = shockwave(p, r, a, t, seed, tint, 1.1);

    // rune band drifting outwards
    float rr = 0.32 + e * 0.48;
    float bandMask = 1.0 - smoothstep(0.02, 0.10, abs(r - rr));
    col += cAlt * 1.5 * glyphRing(r, a, rr, 0.14, 16.0, -e * 2.6 + seed * 1.3, seed) * bandMask;

    // cracks radiating from the impact
    float crack = 1.0 - smoothstep(0.0, 0.055, cracks(p, seed));
    float reach = 1.0 - smoothstep(0.30 + e * 0.55, 0.40 + e * 0.55, r);
    float mask = smoothstep(0.30, 0.62, vnoise(p * 5.5 + seed));
    col += cHot * crack * reach * mask * (1.0 - t) * 1.7;

    // core flash and sparks
    col += cHot * exp(-r * r * 14.0) * (1.0 - smoothstep(0.0, 0.30, t)) * 1.4;
    col += cMain * sparks(p, t, seed) * 0.8;
    return col;
}

// -------------------------------------------------------------------- main
void main() {
    vec2 cell = floor((texCoord0 + 2.0) / 4.0);
    vec2 p = texCoord0 - cell * 4.0;
    float seed = cell.x;
    int kind = int(cell.y + 0.5);

    float t = clamp(vertexColor.a, 0.0, 1.0);
    vec3 tint = vertexColor.rgb;

    float r = length(p);
    if (r > 1.0) {
        discard;
    }
    float a = atan(p.y, p.x);

    vec3 col;
    if (kind == 0) {
        col = takeoffCircle(p, r, a, t, seed, tint);
    } else if (kind == 1) {
        col = landingBurst(p, r, a, t, seed, tint);
    } else if (kind == 2) {
        col = haloRing(p, r, a, t, seed, tint);
    } else if (kind == 3) {
        col = takeoffSpiral(p, r, a, t, seed, tint);
    } else if (kind == 4) {
        col = landingStar(p, r, a, t, seed, tint);
    } else if (kind == 5) {
        col = landingRunes(p, r, a, t, seed, tint);
    } else {
        col = haloRing(p, r, a, t, seed, tint);
    }

    float fade = smoothstep(0.0, 0.04, t) * (1.0 - smoothstep(0.82, 1.0, t));
    float edge = 1.0 - smoothstep(0.93, 1.0, r);

    col = 1.0 - exp(-col * 1.25);
    col *= fade * edge;

    fragColor = vec4(col, 1.0);
}
