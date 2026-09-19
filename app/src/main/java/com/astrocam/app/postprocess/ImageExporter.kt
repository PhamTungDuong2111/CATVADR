package com.astrocam.app.postprocess

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

object ImageExporter {
    private const val TAG = "ImageExporter"

    suspend fun saveJpeg(
        context: Context,
        bitmap: Bitmap,
        stackCount: Int,
        totalExposure: Float
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "AstroCam_${timeStamp}_${stackCount}x_${totalExposure.toInt()}s.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AstroCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Saved stacked astro image to $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save JPEG", e)
            if (uri != null) resolver.delete(uri, null, null)
            uri = null
        }

        uri
    }
    suspend fun saveStandardPhoto(
        context: Context,
        bitmap: Bitmap
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "IMG_${timeStamp}.jpg"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Saved photo to $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo", e)
            if (uri != null) resolver.delete(uri, null, null)
            uri = null
        }

        uri
    }

    suspend fun saveVideoToGallery(
        context: Context,
        tempVideoFile: File
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "VID_${timeStamp}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    tempVideoFile.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Video.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Saved video to $uri")
                try { tempVideoFile.delete() } catch (e: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save video to gallery", e)
            if (uri != null) resolver.delete(uri, null, null)
            uri = null
        }

        uri
    }

    /**
     * Exports 16-bit uncompressed RGB TIFF image for Siril / PixInsight astrophotography post-processing
     */
    suspend fun saveTiff(
        context: Context,
        bitmap: Bitmap,
        stackCount: Int
    ): Uri? = withContext(Dispatchers.IO) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "AstroCam_16bit_${timeStamp}_${stackCount}x.tiff"

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/tiff")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AstroCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        var uri: Uri? = null

        try {
            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    writeSimple16BitTiff(bitmap, stream)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                Log.i(TAG, "Saved 16-bit TIFF image to $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save TIFF", e)
            if (uri != null) resolver.delete(uri, null, null)
            uri = null
        }

        uri
    }

    private fun writeSimple16BitTiff(bitmap: Bitmap, out: OutputStream) {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixelBytes = width * height * 6 // 3 channels * 2 bytes each
        val headerSize = 168

        // Build Little Endian TIFF Header + IFD
        val headerBuf = java.nio.ByteBuffer.allocate(headerSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        // 1. TIFF Header (8 bytes)
        headerBuf.putShort(0x4949.toShort()) // "II" (Little-Endian)
        headerBuf.putShort(42.toShort())      // TIFF Magic 42
        headerBuf.putInt(8)                  // Offset to First IFD (at offset 8)

        // 2. IFD Entry Count (2 bytes) -> 11 directory entries
        headerBuf.putShort(11.toShort())

        fun putTag(tag: Int, type: Int, count: Int, value: Int) {
            headerBuf.putShort(tag.toShort())
            headerBuf.putShort(type.toShort())
            headerBuf.putInt(count)
            headerBuf.putInt(value)
        }

        // Tags:
        putTag(0x0100, 4, 1, width)            // ImageWidth (LONG)
        putTag(0x0101, 4, 1, height)           // ImageLength (LONG)
        putTag(0x0102, 3, 3, 146)              // BitsPerSample (SHORT[3] at offset 146)
        putTag(0x0103, 3, 1, 1)                // Compression = 1 (Uncompressed)
        putTag(0x0106, 3, 1, 2)                // PhotometricInterpretation = 2 (RGB)
        putTag(0x0111, 4, 1, headerSize)       // StripOffsets = 168 (Image data start)
        putTag(0x0115, 3, 1, 3)                // SamplesPerPixel = 3 (RGB)
        putTag(0x0116, 4, 1, height)           // RowsPerStrip = height
        putTag(0x0117, 4, 1, totalPixelBytes)  // StripByteCounts
        putTag(0x011A, 5, 1, 152)              // XResolution (RATIONAL at offset 152)
        putTag(0x011B, 5, 1, 160)              // YResolution (RATIONAL at offset 160)

        // Next IFD Offset (4 bytes)
        headerBuf.putInt(0)

        // 3. Extra tag data:
        // Offset 146: BitsPerSample [16, 16, 16] (6 bytes)
        headerBuf.putShort(16.toShort())
        headerBuf.putShort(16.toShort())
        headerBuf.putShort(16.toShort())

        // Offset 152: XResolution (72 / 1)
        headerBuf.putInt(72)
        headerBuf.putInt(1)

        // Offset 160: YResolution (72 / 1)
        headerBuf.putInt(72)
        headerBuf.putInt(1)

        // Write header & IFD
        out.write(headerBuf.array())

        // 4. Write 16-bit uncompressed RGB pixels
        val pixels = IntArray(width)
        val rowBuffer = ByteArray(width * 6)

        for (y in 0 until height) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)
            var byteIdx = 0
            for (x in 0 until width) {
                val p = pixels[x]
                val r16 = (((p shr 16) and 0xFF) * 257)
                val g16 = (((p shr 8) and 0xFF) * 257)
                val b16 = ((p and 0xFF) * 257)

                // R (Little Endian)
                rowBuffer[byteIdx++] = (r16 and 0xFF).toByte()
                rowBuffer[byteIdx++] = ((r16 shr 8) and 0xFF).toByte()
                // G
                rowBuffer[byteIdx++] = (g16 and 0xFF).toByte()
                rowBuffer[byteIdx++] = ((g16 shr 8) and 0xFF).toByte()
                // B
                rowBuffer[byteIdx++] = (b16 and 0xFF).toByte()
                rowBuffer[byteIdx++] = ((b16 shr 8) and 0xFF).toByte()
            }
            out.write(rowBuffer)
        }
    }
}
