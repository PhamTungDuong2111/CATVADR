#version 300 es
layout (location = 0) in vec2 aPosition;
layout (location = 1) in vec2 aTexCoord;

uniform mat3 uHomographyMatrix; // 3x3 Homography warping matrix
out vec2 vTexCoord;
out vec2 vWarpedTexCoord;

void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
    vTexCoord = aTexCoord;
    // Project texture coordinate through Homography
    vec3 warped = uHomographyMatrix * vec3(aTexCoord, 1.0);
    vWarpedTexCoord = warped.xy / warped.z;
}
