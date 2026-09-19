#version 300 es
precision highp float;

uniform sampler2D uAccumulatedTexture;
uniform float uMidtone;   // Midtone balance (default ~0.15, lower = brighter faint nebulas)
uniform float uBlackPoint;// Black clipping point (default ~0.02)
uniform float uSaturation;// Color saturation factor (default ~1.3)

in vec2 vTexCoord;
out vec4 fragColor;

// PixInsight standard Midtone Transfer Function (MTF)
float mtf(float x, float m) {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    float denom = ((2.0 * m - 1.0) * x) - m;
    if (abs(denom) < 0.00001) return x;
    return ((m - 1.0) * x) / denom;
}

void main() {
    vec4 rawColor = texture(uAccumulatedTexture, vTexCoord);

    // 1. Black Point Subtraction & Normalization
    float bpNorm = max(0.0001, 1.0 - uBlackPoint);
    vec3 c = max(vec3(0.0), (rawColor.rgb - vec3(uBlackPoint)) / bpNorm);

    // 2. Midtone Transfer Function non-linear stretch
    c.r = mtf(c.r, uMidtone);
    c.g = mtf(c.g, uMidtone);
    c.b = mtf(c.b, uMidtone);

    // 3. Saturation boost
    float luminance = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luminance), c, uSaturation);

    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
