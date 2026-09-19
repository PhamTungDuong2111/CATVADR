#pragma once

#include "star_detector.h"
#include <vector>
#include <array>

struct AlignmentResult {
    bool success;
    int inlierCount;
    float avgError;
    // 3x3 Homography / Affine transformation matrix in column-major order (OpenGL compatible)
    std::array<float, 9> matrix;
};

class HomographySolver {
public:
    HomographySolver() = default;
    ~HomographySolver() = default;

    // Matches stars between reference frame and current frame, returning 3x3 alignment matrix normalized for OpenGL UV
    AlignmentResult solve(const std::vector<Star>& refStars,
                         const std::vector<Star>& curStars,
                         float tolerance = 2.5f,
                         int maxRansacIterations = 1000,
                         float imageWidth = 1920.0f,
                         float imageHeight = 1080.0f);

private:
    struct StarMatch {
        int refIdx;
        int curIdx;
    };

    std::vector<StarMatch> matchTriangles(const std::vector<Star>& refStars,
                                          const std::vector<Star>& curStars);
};
