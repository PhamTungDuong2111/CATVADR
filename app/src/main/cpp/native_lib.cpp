#include <jni.h>
#include <android/log.h>
#include <vector>
#include "star_detector.h"
#include "homography_solver.h"

#define TAG "AstroNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_astrocam_app_alignment_StarAlignmentEngine_nativeDetectStars(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray grayData_,
    jint width,
    jint height,
    jfloat threshold,
    jint maxStars,
    jint rowStride) {

    jbyte *grayBytes = env->GetByteArrayElements(grayData_, nullptr);
    if (!grayBytes) return nullptr;

    StarDetector detector(width, height);
    std::vector<Star> stars = detector.detectStars(
        reinterpret_cast<const uint8_t*>(grayBytes),
        width, height, threshold, maxStars, rowStride);

    env->ReleaseByteArrayElements(grayData_, grayBytes, JNI_ABORT);

    // Flatten to [x, y, brightness] triplets
    int count = static_cast<int>(stars.size());
    std::vector<float> flat(count * 3);
    for (int i = 0; i < count; ++i) {
        flat[i * 3 + 0] = stars[i].x;
        flat[i * 3 + 1] = stars[i].y;
        flat[i * 3 + 2] = stars[i].brightness;
    }

    jfloatArray result = env->NewFloatArray(count * 3);
    if (result && count > 0) {
        env->SetFloatArrayRegion(result, 0, count * 3, flat.data());
    }
    return result;
}

JNIEXPORT jfloatArray JNICALL
Java_com_astrocam_app_alignment_StarAlignmentEngine_nativeComputeHomography(
    JNIEnv *env,
    jobject /* this */,
    jfloatArray refStarsFlat_,
    jfloatArray curStarsFlat_,
    jfloat tolerance,
    jfloat imageWidth,
    jfloat imageHeight) {

    jsize refLen = env->GetArrayLength(refStarsFlat_);
    jsize curLen = env->GetArrayLength(curStarsFlat_);
    if (refLen < 9 || curLen < 9) return nullptr; // Need at least 3 stars each

    jfloat *refData = env->GetFloatArrayElements(refStarsFlat_, nullptr);
    jfloat *curData = env->GetFloatArrayElements(curStarsFlat_, nullptr);

    int refCount = refLen / 3;
    int curCount = curLen / 3;

    std::vector<Star> refStars(refCount);
    for (int i = 0; i < refCount; ++i) {
        refStars[i].x = refData[i * 3 + 0];
        refStars[i].y = refData[i * 3 + 1];
        refStars[i].brightness = refData[i * 3 + 2];
    }

    std::vector<Star> curStars(curCount);
    for (int i = 0; i < curCount; ++i) {
        curStars[i].x = curData[i * 3 + 0];
        curStars[i].y = curData[i * 3 + 1];
        curStars[i].brightness = curData[i * 3 + 2];
    }

    env->ReleaseFloatArrayElements(refStarsFlat_, refData, JNI_ABORT);
    env->ReleaseFloatArrayElements(curStarsFlat_, curData, JNI_ABORT);

    HomographySolver solver;
    AlignmentResult alignRes = solver.solve(refStars, curStars, tolerance, 1000, imageWidth, imageHeight);

    if (!alignRes.success) {
        return nullptr;
    }

    jfloatArray matrixArray = env->NewFloatArray(9);
    if (matrixArray) {
        env->SetFloatArrayRegion(matrixArray, 0, 9, alignRes.matrix.data());
    }
    return matrixArray;
}

JNIEXPORT void JNICALL
Java_com_astrocam_app_alignment_StarAlignmentEngine_nativeYuvToRgba(
    JNIEnv *env,
    jobject /* this */,
    jobject yBuf,
    jint yRowStride,
    jobject uBuf,
    jint uRowStride,
    jint uPixelStride,
    jobject vBuf,
    jint vRowStride,
    jint vPixelStride,
    jobject rgbaBuf,
    jint width,
    jint height) {

    const uint8_t *yPtr = reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(yBuf));
    const uint8_t *uPtr = reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(uBuf));
    const uint8_t *vPtr = reinterpret_cast<const uint8_t*>(env->GetDirectBufferAddress(vBuf));
    uint8_t *rgbaPtr = reinterpret_cast<uint8_t*>(env->GetDirectBufferAddress(rgbaBuf));

    if (!yPtr || !uPtr || !vPtr || !rgbaPtr) return;

    for (int y = 0; y < height; ++y) {
        const uint8_t *yRow = yPtr + (y * yRowStride);
        const uint8_t *uRow = uPtr + ((y / 2) * uRowStride);
        const uint8_t *vRow = vPtr + ((y / 2) * vRowStride);
        uint8_t *dst = rgbaPtr + (y * width * 4);

        for (int x = 0; x < width; ++x) {
            int yVal = yRow[x];
            int uIdx = (x / 2) * uPixelStride;
            int vIdx = (x / 2) * vPixelStride;
            int uVal = (int)uRow[uIdx] - 128;
            int vVal = (int)vRow[vIdx] - 128;

            int r = yVal + ((1436 * vVal) >> 10);
            int g = yVal - ((352 * uVal + 731 * vVal) >> 10);
            int b = yVal + ((1815 * uVal) >> 10);

            dst[0] = (uint8_t)(r < 0 ? 0 : (r > 255 ? 255 : r));
            dst[1] = (uint8_t)(g < 0 ? 0 : (g > 255 ? 255 : g));
            dst[2] = (uint8_t)(b < 0 ? 0 : (b > 255 ? 255 : b));
            dst[3] = 255;
            dst += 4;
        }
    }
}

} // extern "C"
