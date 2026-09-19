#include "homography_solver.h"
#include <cmath>
#include <algorithm>
#include <random>
#include <map>

namespace {
    inline float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return std::sqrt(dx * dx + dy * dy);
    }

    struct Triangle {
        int i, j, k;
        float r1, r2; // Invariant side ratios (sorted: a <= b <= c, r1 = a/c, r2 = b/c)
        float maxSide;
    };

    std::vector<Triangle> buildTriangles(const std::vector<Star>& stars, int maxStarsToUse = 30) {
        std::vector<Triangle> triangles;
        int n = std::min((int)stars.size(), maxStarsToUse);
        if (n < 3) return triangles;

        for (int i = 0; i < n - 2; ++i) {
            for (int j = i + 1; j < n - 1; ++j) {
                float d1 = distance(stars[i].x, stars[i].y, stars[j].x, stars[j].y);
                if (d1 < 10.0f) continue;

                for (int k = j + 1; k < n; ++k) {
                    float d2 = distance(stars[j].x, stars[j].y, stars[k].x, stars[k].y);
                    float d3 = distance(stars[i].x, stars[i].y, stars[k].x, stars[k].y);
                    if (d2 < 10.0f || d3 < 10.0f) continue;

                    float sides[3] = {d1, d2, d3};
                    std::sort(sides, sides + 3);

                    // Skip degenerate or overly acute triangles
                    if (sides[2] < 15.0f || sides[0] / sides[2] < 0.1f) continue;

                    Triangle tri;
                    tri.i = i; tri.j = j; tri.k = k;
                    tri.r1 = sides[0] / sides[2];
                    tri.r2 = sides[1] / sides[2];
                    tri.maxSide = sides[2];
                    triangles.push_back(tri);
                }
            }
        }
        return triangles;
    }
}

std::vector<HomographySolver::StarMatch> HomographySolver::matchTriangles(
    const std::vector<Star>& refStars, const std::vector<Star>& curStars) {

    std::vector<StarMatch> matches;
    auto refTriangles = buildTriangles(refStars, 25);
    auto curTriangles = buildTriangles(curStars, 25);

    if (refTriangles.empty() || curTriangles.empty()) return matches;

    std::map<std::pair<int, int>, int> voteMap;
    const float ratioTolerance = 0.015f; // Invariant ratio tolerance

    for (const auto& rt : refTriangles) {
        for (const auto& ct : curTriangles) {
            if (std::abs(rt.r1 - ct.r1) < ratioTolerance &&
                std::abs(rt.r2 - ct.r2) < ratioTolerance) {
                // Approximate scale consistency check
                float scaleRatio = ct.maxSide / rt.maxSide;
                if (scaleRatio > 0.85f && scaleRatio < 1.15f) {
                    voteMap[{rt.i, ct.i}]++;
                    voteMap[{rt.j, ct.j}]++;
                    voteMap[{rt.k, ct.k}]++;
                }
            }
        }
    }

    // Collect high confidence matches
    std::map<int, int> bestCurForRef;
    std::map<int, int> bestScore;
    int minVotes = (refTriangles.size() <= 2 || curTriangles.size() <= 2) ? 1 : 2;
    for (const auto& item : voteMap) {
        int refIdx = item.first.first;
        int curIdx = item.first.second;
        int votes = item.second;
        if (votes >= minVotes && votes > bestScore[refIdx]) {
            bestScore[refIdx] = votes;
            bestCurForRef[refIdx] = curIdx;
        }
    }

    for (const auto& p : bestCurForRef) {
        matches.push_back({p.first, p.second});
    }

    return matches;
}

AlignmentResult HomographySolver::solve(const std::vector<Star>& refStars,
                                       const std::vector<Star>& curStars,
                                       float tolerance,
                                       int maxRansacIterations,
                                       float imageWidth,
                                       float imageHeight) {
    AlignmentResult result;
    // Default Identity Matrix (Column-Major)
    result.matrix = {
        1.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 1.0f
    };
    result.success = false;
    result.inlierCount = 0;
    result.avgError = 999.0f;

    auto matches = matchTriangles(refStars, curStars);
    if (matches.size() < 3) {
        return result; // Need at least 3 correspondences to solve 2D similarity transform
    }

    std::mt19937 rng(1337);
    int bestInliers = 0;
    float bestA = 1.0f, bestB = 0.0f, bestTx = 0.0f, bestTy = 0.0f;
    float bestError = 999.0f;

    // RANSAC using 2-point similarity transform:
    // [x'] = [s*cos  -s*sin] [x] + [tx]
    // [y']   [s*sin   s*cos] [y]   [ty]
    for (int iter = 0; iter < maxRansacIterations; ++iter) {
        int idx1 = rng() % matches.size();
        int idx2 = rng() % matches.size();
        if (idx1 == idx2) continue;

        const auto& m1 = matches[idx1];
        const auto& m2 = matches[idx2];

        float rx1 = refStars[m1.refIdx].x, ry1 = refStars[m1.refIdx].y;
        float rx2 = refStars[m2.refIdx].x, ry2 = refStars[m2.refIdx].y;
        float cx1 = curStars[m1.curIdx].x, cy1 = curStars[m1.curIdx].y;
        float cx2 = curStars[m2.curIdx].x, cy2 = curStars[m2.curIdx].y;

        float drx = rx2 - rx1, dry = ry2 - ry1;
        float dcx = cx2 - cx1, dcy = cy2 - cy1;
        float dRefSq = drx * drx + dry * dry;
        if (dRefSq < 25.0f) continue;

        // Similarity transform: Ref -> Cur or Cur -> Ref?
        // We want to warp Cur into Ref coordinates: P_ref = H * P_cur
        // Solve for (a, b, tx, ty) where:
        // [a  -b] [cx] + [tx] = [rx]
        // [b   a] [cy] + [ty] = [ry]
        float a = (drx * dcx + dry * dcy) / (dcx * dcx + dcy * dcy);
        float b = (dry * dcx - drx * dcy) / (dcx * dcx + dcy * dcy);
        float tx = rx1 - (a * cx1 - b * cy1);
        float ty = ry1 - (b * cx1 + a * cy1);

        // Disallow extreme rotation or scaling (> 15% scale drift)
        float scale = std::sqrt(a * a + b * b);
        if (scale < 0.85f || scale > 1.15f) continue;

        // Count inliers
        int inliers = 0;
        float totalErr = 0.0f;
        for (const auto& m : matches) {
            float cx = curStars[m.curIdx].x;
            float cy = curStars[m.curIdx].y;
            float predX = a * cx - b * cy + tx;
            float predY = b * cx + a * cy + ty;

            float rx = refStars[m.refIdx].x;
            float ry = refStars[m.refIdx].y;
            float err = distance(predX, predY, rx, ry);
            if (err <= tolerance) {
                inliers++;
                totalErr += err;
            }
        }

        if (inliers > bestInliers) {
            bestInliers = inliers;
            bestError = totalErr / inliers;
            bestA = a;
            bestB = b;
            bestTx = tx;
            bestTy = ty;

            if (inliers == (int)matches.size()) break;
        }
    }

    int requiredInliers = (matches.size() == 3) ? 3 : 4;
    if (bestInliers >= requiredInliers && bestInliers >= (int)(matches.size() * 0.35f)) {
        result.success = true;
        result.inlierCount = bestInliers;
        result.avgError = bestError;

        if (imageWidth > 1.0f && imageHeight > 1.0f) {
            // Invert the Cur -> Ref similarity transform so the vertex shader maps Ref -> Cur
            float sSq = bestA * bestA + bestB * bestB;
            if (sSq > 1e-6f) {
                float invA = bestA / sSq;
                float invB = -bestB / sSq;
                float invTx = -invA * bestTx + invB * bestTy;
                float invTy = -invB * bestTx - invA * bestTy;

                // Convert to normalized UV coordinates:
                float aspectW = imageWidth / imageHeight;
                float aspectH = imageHeight / imageWidth;

                // Column-major 3x3 matrix for OpenGL ES:
                result.matrix[0] = invA;
                result.matrix[1] = invB * aspectW;
                result.matrix[2] = 0.0f;

                result.matrix[3] = -invB * aspectH;
                result.matrix[4] = invA;
                result.matrix[5] = 0.0f;

                result.matrix[6] = invTx / imageWidth;
                result.matrix[7] = invTy / imageHeight;
                result.matrix[8] = 1.0f;
            }
        } else {
            result.matrix[0] = bestA;    result.matrix[3] = -bestB;   result.matrix[6] = bestTx;
            result.matrix[1] = bestB;    result.matrix[4] = bestA;    result.matrix[7] = bestTy;
            result.matrix[2] = 0.0f;     result.matrix[5] = 0.0f;     result.matrix[8] = 1.0f;
        }
    }

    return result;
}
