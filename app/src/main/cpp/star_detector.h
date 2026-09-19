#pragma once

#include <vector>
#include <cstdint>

struct Star {
    float x;          // Sub-pixel X coordinate
    float y;          // Sub-pixel Y coordinate
    float brightness; // Peak or integrated brightness
    float fwhm;       // Full width at half maximum (size estimate)
};

class StarDetector {
public:
    StarDetector(int width, int height);
    ~StarDetector() = default;

    // Detect stars in 8-bit grayscale image (e.g. Y plane of YUV_420_888 or downscaled RAW)
    std::vector<Star> detectStars(const uint8_t* grayData, int width, int height,
                                  float sigmaThreshold = 3.5f, int maxStars = 150,
                                  int rowStride = 0);
};
