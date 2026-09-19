#version 300 es
precision highp float;

uniform sampler2D uCurrentFrame;
uniform sampler2D uAccumulatorTexture;
uniform int uFrameCount;

in vec2 vTexCoord;
out vec4 fragColor;

void main() {
    vec4 curColor = texture(uCurrentFrame, vTexCoord);

    if (uFrameCount <= 1) {
        fragColor = curColor;
    } else {
        vec4 prevAccum = texture(uAccumulatorTexture, vTexCoord);
        // Maximum intensity projection across frames preserves bright star paths
        fragColor = max(prevAccum, curColor);
    }
}
