package com.researchagent.autofill.ocr

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.annotation.TargetApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.researchagent.autofill.core.Bounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

data class OcrLine(val text: String, val bounds: Bounds)

/**
 * OCR on-device (ML Kit, modelo embutido — nada sai do aparelho).
 * Usado como fallback quando a árvore de acessibilidade não expõe texto (Seções 11 e 40).
 */
class OcrEngine(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var lastCaptureAt = 0L

    @TargetApi(30)
    suspend fun captureAndRecognize(service: AccessibilityService): List<OcrLine> {
        // takeScreenshot tem limite de frequência no sistema
        val wait = 1100 - (System.currentTimeMillis() - lastCaptureAt)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastCaptureAt = System.currentTimeMillis()
        val bmp = capture(service) ?: return emptyList()
        return try { recognize(bmp) } finally { bmp.recycle() }
    }

    @TargetApi(30)
    private suspend fun capture(service: AccessibilityService): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Bitmap?> { cont ->
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        val bmp = try {
                            Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.let { hw ->
                                hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
                            }
                        } catch (e: Exception) { null } finally { buffer.close() }
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) { if (cont.isActive) cont.resume(null) }
                })
        }
    }

    suspend fun recognize(bitmap: Bitmap): List<OcrLine> = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks.flatMap { block ->
                    block.lines.map { line ->
                        val b = line.boundingBox
                        OcrLine(line.text, if (b != null) Bounds(b.left, b.top, b.right, b.bottom) else Bounds.EMPTY)
                    }
                }
                if (cont.isActive) cont.resume(lines)
            }
            .addOnFailureListener { if (cont.isActive) cont.resume(emptyList()) }
    }

    companion object {
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= 30
    }
}
