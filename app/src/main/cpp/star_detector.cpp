#include "star_detector.h"
#include <cmath>
#include <algorithm>

StarDetector::StarDetector(int /*width*/, int /*height*/) {}

std::vector<Star> StarDetector::detectStars(const uint8_t* grayData, int width, int height,
                                            float sigmaThreshold, int maxStars, int rowStride) {
    std::vector<Star> detected;
    if (!grayData || width <= 10 || height <= 10) return detected;

    int stride = (rowStride > 0) ? rowStride : width;

    // 1. Fast Background & Noise Floor Estimation using 16x16 sampling grid
    double sum = 0.0;
    double sqSum = 0.0;
    int sampleCount = 0;
    int stepX = std::max(4, width / 64);
    int stepY = std::max(4, height / 64);

    for (int y = stepY; y < height - stepY; y += stepY) {
        int rowOffset = y * stride;
        for (int x = stepX; x < width - stepX; x += stepX) {
            uint8_t val = grayData[rowOffset + x];
            sum += val;
            sqSum += (double)val * val;
            sampleCount++;
        }
    }

    if (sampleCount == 0) return detected;

    double mean = sum / sampleCount;
    double variance = (sqSum / sampleCount) - (mean * mean);
    double stddev = (variance > 0.0) ? std::sqrt(variance) : 1.0;
    double threshold = mean + (sigmaThreshold * stddev);
    if (threshold < 15.0) threshold = 15.0; // Avoid near-black noise

    // 2. Scan image for local maxima above threshold
    // Keep 4-pixel border safe for 3x3 centroid calculation
    for (int y = 4; y < height - 4; ++y) {
        int rowOffset = y * stride;
        for (int x = 4; x < width - 4; ++x) {
            uint8_t centerVal = grayData[rowOffset + x];
            if (centerVal < threshold) continue;

            // Check if center is strictly greater than 8-neighbors (local peak)
            bool isPeak = true;
            for (int dy = -1; dy <= 1 && isPeak; ++dy) {
                int neighborRow = (y + dy) * stride;
                for (int dx = -1; dx <= 1; ++dx) {
                    if (dx == 0 && dy == 0) continue;
                    if (grayData[neighborRow + x + dx] >= centerVal) {
                        isPeak = false;
                        break;
                    }
                }
            }

            if (!isPeak) continue;

            // Reject hot pixels: At least 2 neighbor pixels must have energy > threshold/2
            int neighborCount = 0;
            float halfThresh = threshold * 0.4f;
            for (int dy = -1; dy <= 1; ++dy) {
                int neighborRow = (y + dy) * stride;
                for (int dx = -1; dx <= 1; ++dx) {
                    if (dx == 0 && dy == 0) continue;
                    if (grayData[neighborRow + x + dx] > halfThresh) {
                        neighborCount++;
                    }
                }
            }
            if (neighborCount < 2) continue; // Likely hot pixel

            // 3. Sub-pixel centroid using intensity moments in 3x3 window
            double momentSum = 0.0;
            double momentX = 0.0;
            double momentY = 0.0;

            for (int dy = -1; dy <= 1; ++dy) {
                int neighborRow = (y + dy) * stride;
                for (int dx = -1; dx <= 1; ++dx) {
                    double intensity = (double)grayData[neighborRow + x + dx] - mean;
                    if (intensity < 0.0) intensity = 0.0;
                    momentSum += intensity;
                    momentX += (x + dx) * intensity;
                    momentY += (y + dy) * intensity;
                }
            }

            if (momentSum > 0.0) {
                Star star;
                star.x = static_cast<float>(momentX / momentSum);
                star.y = static_cast<float>(momentY / momentSum);
                star.brightness = static_cast<float>(momentSum);
                star.fwhm = 1.5f; // Estimated PSF width
                detected.push_back(star);
            }
        }
    }

    // 4. Sort by brightness descending and limit count
    std::sort(detected.begin(), detected.end(), [](const Star& a, const Star& b) {
        return a.brightness > b.brightness;
    });

    if ((int)detected.size() > maxStars) {
        detected.resize(maxStars);
    }

    return detected;
}
