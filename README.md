# AstroCam (ChupAnhTV) - Android Astrophotography App (Live Stacking & Star Alignment)

A native Android application inspired by the mechanics of **AstroShader (iOS)** and **Google Camera Astrophotography**, specialized for capturing the Milky Way, nebulae, deep sky objects, and star trails on Android smartphones.

---

## 📍 Project Location & APK Installation

- **Android Studio Project Path**: `c:\Users\Admin\Downloads\ChupAnhTV-fixed`
- **Installation APK**:
  - `ChupAnhTV.apk` (Root directory)
  - Debug build output: `app/build/outputs/apk/debug/app-debug.apk`

> 📱 **Note**: The APK is built for standard Android systems (including native libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`), compatible with physical devices across manufacturers (Samsung, Google Pixel, Xiaomi, Oppo, Vivo, Realme, etc.).

---

## 🚀 Opening and Running in Android Studio

1. Open **Android Studio**.
2. Select **File -> Open...** (or Open Project).
3. Navigate to the project directory.
4. Click **OK**; Android Studio will automatically sync Gradle (integrated Gradle Wrapper 8.4 and Android SDK config).
5. Connect an Android device (with USB Debugging enabled) or start an emulator -> Press **Run (Shift + F10)**.

---

## 🌌 Key Technical Features

### 1. In-Depth Camera Control (Camera2 API Native)
- **Focus at Infinity**: Locks `LENS_FOCUS_DISTANCE = 0.0f` to keep stars pin-sharp throughout long exposures without night-time hunting.
- **Manual ISO Control**: Quick presets or sliding adjustment from `ISO 400` to `ISO 12800` to capture faint nebular emissions.
- **Shutter Speeds**: Presets for 0.5s, 1s, 2s, 4s, 8s, 15s, and 30s.
- **OEM Noise Reduction Bypass**: Disables hardware `NOISE_REDUCTION_MODE` to prevent subtle star dots from being blurred out.

### 2. Star Alignment Algorithms
- **Star Detection**: Identifies local brightness peaks and calculates subpixel star centroids in C++ (Android NDK) with a robust Kotlin fallback algorithm.
- **Constellation Matching & Earth Rotation Compensation**:
  - Utilizes asterism triangle matching combined with RANSAC to derive affine homography transformation matrices across consecutive frames.
  - Automatically translates and rotates incoming frames to achieve sub-pixel alignment with reference frames before accumulation.

### 3. Real-Time Stacking Engine (Live Stacking - OpenGL ES 3.0)
- **🌌 Milky Way (Deep Sky Average Stacking)**: Accumulates and averages pixel values in a 32-bit floating point buffer (FP32), eliminating high-ISO noise, smoothing black sky backgrounds, and revealing faint Milky Way dust lanes.
- **🛰️ Satellite & Airplane Removal (Kappa-Sigma Clipping)**: Discards transient outlier pixels across frames to eliminate plane trails and Starlink satellite streaks.
- **🌠 Star Trails (Maximum Blend)**: Retains maximum luminance per pixel across frames to produce concentric star trail arcs around Polaris.

### 4. Astrophotography UI/UX
- **Red Night Mode**: Night-vision preservation mode (deep red wavelengths > 650nm), adapting the entire interface into deep red tones to preserve scotopic dark adaptation.
- **Artificial Horizon Leveler**:
  - Combines device gyroscope, accelerometer, and magnetometer sensors.
  - Smoothly rotates the virtual horizon with tripod tilt and turns green when the device is level (within ±1.5°).
  - Displays sky pitch elevation and cardinal compass heading (N, S, E, W).
- **Real-Time Histogram**: Visualizes shadows, midtones, and highlights dynamic range distribution in real time.
- **Dynamic MTF / Log Stretch**: Stretches midtones and sets black points on-the-fly directly in the live OpenGL viewfinder.
- **🧪 Sky Simulator Mode**: Generates synthetic star fields with simulated sky rotation, allowing full testing of detection, tracking, and stacking during daylight or indoors.
- **Result Inspector & Export**:
  - High-resolution preview upon exposure completion.
  - One-tap save to the device photo gallery (MediaStore).

### 5. AR Planetary & Deep Sky Overlay (Solar System Engine)
- **Orbital Mechanics (Paul Schlyter & Jean Meeus)**:
  - Real-time altitude and azimuth coordinates for the Sun, Moon (with lunar phases), Mercury, Venus, Mars, Jupiter, Saturn, Uranus, Neptune, the Galactic Center, and key Deep Sky targets (Andromeda Galaxy, Orion Nebula, Pleiades, Sirius, Polaris).
  - Automatically incorporates GPS location and topocentric adjustments.
- **Augmented Reality Viewfinder**:
  - 3D pinhole perspective projection displaying celestial bodies aligned to sensor orientation matrices.
  - Detailed custom rendering: Saturn's rings, Jupiter's atmospheric cloud bands, Mars' reddish hue, Venus sparkle flares, and Moon craters.
  - Ecliptic line tracing planetary paths across the celestial sphere.
- **Off-Screen Directional Indicators**:
  - Screen-edge directional pills guide the user directly towards celestial bodies even when pointing away from targets.
- **Target Guidance Lock & Planet Quick Bar**:
  - Target any body to activate radar tracking reticles and guidance banners until locked (`🎯 TARGET LOCKED`).
  - Detailed scientific inspection sheets: distance, magnitude, constellation, diameter, and astronomical observation tips.

