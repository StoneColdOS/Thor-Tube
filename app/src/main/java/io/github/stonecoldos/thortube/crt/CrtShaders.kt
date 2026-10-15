package io.github.stonecoldos.thortube.crt

/**
 * The CRT look, in one fragment shader.
 *
 * Pipeline per pixel: bend the glass (barrel curve) -> letterbox the 16:9 video inside the
 * 4:3 tube -> soften horizontally like a real beam -> scanlines -> RGB phosphor mask ->
 * vignette and a hint of flicker -> mix in static when tuning.
 */
internal object CrtShaders {

    const val VERTEX = """
attribute vec2 aPos;
varying vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

    const val FRAGMENT = """
#extension GL_OES_EGL_image_external : require
precision highp float;

uniform samplerExternalOES uTex;
uniform mat4 uST;            // SurfaceTexture transform
uniform vec2 uRes;           // glass size in pixels
uniform float uVideoAspect;  // video width / height (0 = no video yet)
uniform float uTime;         // seconds
uniform float uStatic;       // 0 = clear picture, 1 = full static
uniform float uCrt;          // 1 = CRT effects on, 0 = plain video
uniform float uScale;        // picture size: 1 = full glass, <1 = smaller picture in a dark tube
uniform float uFill;         // 1 = crop to fill the glass, 0 = fit with bars
uniform float uCurve;        // glass bulge: 1 = normal, lower = flatter
uniform float uScan;         // scanline strength
uniform float uSoft;         // extra beam blur (cheap sets)
uniform float uGrain;        // constant faint picture noise (cheap sets)
uniform float uMask;         // how dark the gaps between phosphor stripes are
uniform float uGlow;         // bloom around bright parts
uniform float uVig;          // how much the corners darken
uniform float uBoost;        // brightness make-up for lines and mask
uniform float uConv;         // red/blue convergence error
uniform float uSat;          // color saturation
uniform float uMono;         // 1 = black and white set
uniform float uJunk;         // 1 = worn-out set: ghosting, rolling bar, wobble, washed out

varying vec2 vUv;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Bend flat coordinates onto curved glass. Bigger divisors = flatter tube.
vec2 curve(vec2 uv) {
    uv = uv * 2.0 - 1.0;
    vec2 off = abs(uv.yx) * uCurve / vec2(5.5, 4.5);
    uv = uv + uv * off * off;
    return uv * 0.5 + 0.5;
}

// Video sampled in glass space, letterboxed to keep its shape.
vec3 video(vec2 uv) {
    if (uVideoAspect <= 0.0) return vec3(0.0);
    float glass = uRes.x / uRes.y;
    vec2 p = (uv - 0.5) / uScale + 0.5;
    vec2 v = p;
    if (uFill > 0.5) {
        // Crop the long side so the picture covers the whole tube.
        if (uVideoAspect > glass) v.x = (p.x - 0.5) * (glass / uVideoAspect) + 0.5;
        else v.y = (p.y - 0.5) * (uVideoAspect / glass) + 0.5;
    } else if (uVideoAspect > glass) {
        float h = glass / uVideoAspect;
        v.y = (p.y - (1.0 - h) * 0.5) / h;
    } else {
        float w = uVideoAspect / glass;
        v.x = (p.x - (1.0 - w) * 0.5) / w;
    }
    if (v.x < 0.0 || v.x > 1.0 || v.y < 0.0 || v.y > 1.0) return vec3(0.0);
    return texture2D(uTex, (uST * vec4(v, 0.0, 1.0)).xy).rgb;
}

void main() {
    vec2 uv = mix(vUv, curve(vUv), uCrt);
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }

    // Tuning jitter: lines slide sideways a little while static is on.
    float row = floor(gl_FragCoord.y / 3.0);
    uv.x += (hash(vec2(row, floor(uTime * 30.0))) - 0.5) * 0.02 * uStatic;

    // A worn-out set: once every few minutes the picture loses vertical hold for a split second.
    float hiccup = step(0.997, hash(vec2(floor(uTime * 2.0), 9.0))) * step(fract(uTime * 2.0), 0.25);
    uv.y = fract(uv.y + 0.06 * hiccup * uJunk);

    vec3 col;
    if (uCrt > 0.5) {
        float px = 1.0 / uRes.x;
        float py = 1.0 / uRes.y;
        // Soft horizontal beam plus a slight red/blue convergence error.
        vec3 c = video(uv);
        float spread = max(0.5, 1.5 + uSoft * 1.5);
        vec3 l = video(uv - vec2(px * spread, 0.0));
        vec3 r = video(uv + vec2(px * spread, 0.0));
        col = c * 0.6 + (l + r) * 0.2;
        col.r = mix(col.r, r.r, uConv);
        col.b = mix(col.b, l.b, uConv);

        if (uJunk > 0.0) {
            // Smear up and down too, and a ghost of the picture off to the right (bad antenna).
            vec3 v = (video(uv + vec2(0.0, py * 2.5)) + video(uv - vec2(0.0, py * 2.5))) * 0.5;
            col = mix(col, v, 0.35 * uJunk);
            col += video(uv - vec2(0.014, 0.0)) * 0.22 * uJunk;
            col /= 1.0 + 0.22 * uJunk;
        }

        // Color: saturation, or black and white with the bluish cast of an old set.
        float lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = max(mix(vec3(lum), col, uSat), 0.0);
        col = mix(col, lum * vec3(0.9, 0.97, 1.08), uMono);

        // Scanlines: one bright line every 4 screen pixels.
        float scan = 0.5 + 0.5 * cos(gl_FragCoord.y * 1.5708);
        col *= mix(1.0 - 0.38 * uScan, 1.0, scan);

        // Aperture grille: alternating R, G, B phosphor columns (gray on a black and white set).
        float m = mod(floor(gl_FragCoord.x), 3.0);
        vec3 mask = vec3(1.0 - uMask);
        if (m < 1.0) mask.r = 1.0; else if (m < 2.0) mask.g = 1.0; else mask.b = 1.0;
        mask = mix(mask, vec3(dot(mask, vec3(0.333))), uMono);
        col *= mask;

        // Make up for the light lost to lines and mask, then a little glow.
        col *= uBoost;
        vec3 glow = pow(c, vec3(2.0));
        glow = mix(glow, vec3(dot(glow, vec3(0.299, 0.587, 0.114))), uMono);
        col += glow * uGlow;

        if (uJunk > 0.0) {
            // Washed-out blacks, a dark bar slowly rolling up, and an uneven flicker.
            col = mix(vec3(0.07), col, 1.0 - 0.22 * uJunk);
            float y = fract(uv.y + uTime * 0.025) - 0.5;
            col *= 1.0 - 0.1 * exp(-y * y * 60.0) * uJunk;
            col *= 1.0 - 0.07 * hash(vec2(floor(uTime * 12.0), 7.0)) * uJunk;
        }

        // Darker toward the edges of the tube, and a faint 60 Hz shimmer.
        float vig = 16.0 * uv.x * uv.y * (1.0 - uv.x) * (1.0 - uv.y);
        col *= pow(vig, uVig);
        col *= 0.985 + 0.015 * sin(uTime * 377.0);
        col += (hash(floor(gl_FragCoord.xy / 2.0) + floor(uTime * 30.0) * 0.71) - 0.5) * 0.07 * uGrain;

        // Soft fall-off right at the edge of the glass.
        vec2 edge = smoothstep(0.0, 0.012, uv) * smoothstep(0.0, 0.012, 1.0 - uv);
        col *= edge.x * edge.y;
    } else {
        col = video(uv);
    }

    // Static: fresh noise every frame, with a brighter band rolling down the screen.
    float n = hash(floor(gl_FragCoord.xy / 2.0) + floor(uTime * 60.0) * 1.37);
    float band = smoothstep(0.92, 1.0, sin((uv.y + uTime * 0.7) * 6.2832 * 1.5));
    vec3 snow = vec3(n * 0.85 + band * 0.25);
    col = mix(col, snow, clamp(uStatic, 0.0, 1.0));

    gl_FragColor = vec4(col, 1.0);
}
"""
}
