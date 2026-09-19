#version 300 es
precision highp float;

uniform sampler2D uCurrentFrame;
uniform sampler2D uAccumulatorTexture;
uniform int uFrameCount;
uniform int uStackingMode; // 0 = Deep Sky Average, 1 = Kappa-Sigma Satellite Clipping

in vec2 vTexCoord;
in vec2 vWarpedTexCoord;
out vec4 fragColor;

void main() {
    // Check if warped coordinate lies within valid texture boundaries of current frame
    if (vWarpedTexCoord.x < 0.0 || vWarpedTexCoord.x > 1.0 ||
        vWarpedTexCoord.y < 0.0 || vWarpedTexCoord.y > 1.0) {
        // Outside warped boundary: retain previous accumulated reference value
        fragColor = texture(uAccumulatorTexture, vTexCoord);
        return;
    }

    vec4 curColor = texture(uCurrentFrame, vWarpedTexCoord);

    if (uFrameCount <= 1) {
        fragColor = curColor;
    } else {
        vec4 prevAccum = texture(uAccumulatorTexture, vTexCoord);
        float weight = 1.0 / float(uFrameCount);

        // Kappa-Sigma Outlier Clipping (Satellite trails & Airplane flash rejection)
        if (uStackingMode == 1 && uFrameCount >= 3) {
            vec3 diff = curColor.rgb - prevAccum.rgb;
            float maxDiff = max(max(diff.r, diff.g), diff.b);
            // Sudden bright streak above moving average: reject transient outlier
            if (maxDiff > 0.18) {
                fragColor = prevAccum;
                return;
            }
        }

        // Recursive moving average: Prev * (N-1)/N + Cur * (1/N)
        fragColor = mix(prevAccum, curColor, weight);
    }
}
