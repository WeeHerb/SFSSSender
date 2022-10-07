package com.mslxl.sfss.util

import android.annotation.SuppressLint
import android.graphics.Rect
import android.hardware.Camera
import android.hardware.Camera.CameraInfo
import android.hardware.Camera.PreviewCallback
import android.util.Log
import android.view.SurfaceHolder
import com.google.zxing.PlanarYUVLuminanceSource
import java.io.IOException
import java.util.*


class CameraManager {
    private var camera: Camera? = null;
    private var cameraResolution: Camera.Size? = null
    var frame: Rect? = null
        private set
    var framePreview: Rect? = null
        private set

    @Throws(IOException::class)
    fun open(
        holder: SurfaceHolder,
        continuousAutoFocus: Boolean
    ): Camera? {
        // try back-facing camera
        camera = Camera.open()

        // fall back to using front-facing camera
        if (camera == null) {
            val cameraCount = Camera.getNumberOfCameras()
            val cameraInfo = CameraInfo()

            // search for front-facing camera
            for (i in 0 until cameraCount) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == CameraInfo.CAMERA_FACING_FRONT) {
                    camera = Camera.open(i)
                    break
                }
            }
        }
        val camera = this.camera!!
        camera.setDisplayOrientation(90)
        camera.setPreviewDisplay(holder)
        val parameters = camera.getParameters()
        val surfaceFrame = holder.surfaceFrame
        cameraResolution = findBestPreviewSizeValue(parameters, surfaceFrame)
        val surfaceWidth = surfaceFrame.width()
        val surfaceHeight = surfaceFrame.height()
        val rawSize = Math.min(
            surfaceWidth * 4 / 5,
            surfaceHeight * 4 / 5
        )
        val frameSize = Math.max(
            MIN_FRAME_SIZE,
            Math.min(MAX_FRAME_SIZE, rawSize)
        )
        val leftOffset = (surfaceWidth - frameSize) / 2
        val topOffset = (surfaceHeight - frameSize) / 2
        frame = Rect(
            leftOffset, topOffset, leftOffset + frameSize,
            topOffset + frameSize
        )
        framePreview = Rect(
            frame!!.left * cameraResolution!!.height
                    / surfaceWidth, frame!!.top * cameraResolution!!.width
                    / surfaceHeight, frame!!.right * cameraResolution!!.height
                    / surfaceWidth, frame!!.bottom * cameraResolution!!.width
                    / surfaceHeight
        )
        val savedParameters = parameters?.flatten()
        try {
            setDesiredCameraParameters(
                camera, cameraResolution,
                continuousAutoFocus
            )
        } catch (x: RuntimeException) {
            if (savedParameters != null) {
                val parameters2 = camera.getParameters()
                parameters2.unflatten(savedParameters)
                try {
                    camera.setParameters(parameters2)
                    setDesiredCameraParameters(
                        camera, cameraResolution,
                        continuousAutoFocus
                    )
                } catch (x2: RuntimeException) {
                    Log.i("problem setting camera parameters", x2.toString())
                }
            }
        }
        camera.startPreview()
        return camera
    }

    fun close() {
        if (camera != null) {
            camera!!.stopPreview()
            camera!!.release()
            camera = null
        }
    }

    fun requestPreviewFrame(callback: PreviewCallback?) {
        camera!!.setOneShotPreviewCallback(callback)
    }

    fun buildLuminanceSource(data: ByteArray?): PlanarYUVLuminanceSource {
        return PlanarYUVLuminanceSource(
            data, cameraResolution!!.width,
            cameraResolution!!.height, framePreview!!.top, framePreview!!.left,
            framePreview!!.height(), framePreview!!.width(), false
        )
    }

    fun setTorch(enabled: Boolean) {
        if (camera == null) {
            return
        }
        if (enabled != getTorchEnabled(camera!!)) setTorchEnabled(
            camera!!, enabled
        )
    }

    fun torchEnabled(): Boolean {
        return if (camera == null) {
            false
        } else getTorchEnabled(camera!!)
    }

    companion object {
        private const val MIN_FRAME_SIZE = 320
        private const val MAX_FRAME_SIZE = 1000
        private const val MIN_PREVIEW_PIXELS = 470 * 320 // normal screen
        private const val MAX_PREVIEW_PIXELS = 1280 * 720
        private val numPixelComparator: Comparator<Camera.Size> =
            Comparator<Camera.Size> { size1, size2 ->
                val pixels1 = size1.height * size1.width
                val pixels2 = size2.height * size2.width
                if (pixels1 < pixels2) 1 else if (pixels1 > pixels2) -1 else 0
            }

        private fun findBestPreviewSizeValue(
            parameters: Camera.Parameters?, surfaceResolution: Rect
        ): Camera.Size {
            var surfaceResolution = surfaceResolution
            if (surfaceResolution.height() > surfaceResolution.width()) surfaceResolution = Rect(
                0, 0, surfaceResolution.height(),
                surfaceResolution.width()
            )
            val screenAspectRatio =
                surfaceResolution.width().toFloat() / surfaceResolution.height().toFloat()
            val rawSupportedSizes = parameters
                ?.getSupportedPreviewSizes() ?: return parameters!!.previewSize

            // sort by size, descending
            val supportedPreviewSizes: List<Camera.Size> = ArrayList(
                rawSupportedSizes
            )
            Collections.sort(supportedPreviewSizes, numPixelComparator)
            var bestSize: Camera.Size? = null
            var diff = Float.POSITIVE_INFINITY
            for (supportedPreviewSize in supportedPreviewSizes) {
                val realWidth = supportedPreviewSize.width
                val realHeight = supportedPreviewSize.height
                val realPixels = realWidth * realHeight
                if (realPixels < MIN_PREVIEW_PIXELS
                    || realPixels > MAX_PREVIEW_PIXELS
                ) continue
                val isCandidatePortrait = realWidth < realHeight
                val maybeFlippedWidth = if (isCandidatePortrait) realHeight else realWidth
                val maybeFlippedHeight = if (isCandidatePortrait) realWidth else realHeight
                if (maybeFlippedWidth == surfaceResolution.width()
                    && maybeFlippedHeight == surfaceResolution.height()
                ) return supportedPreviewSize
                val aspectRatio = maybeFlippedWidth.toFloat() / maybeFlippedHeight.toFloat()
                val newDiff = Math.abs(aspectRatio - screenAspectRatio)
                if (newDiff < diff) {
                    bestSize = supportedPreviewSize
                    diff = newDiff
                }
            }
            return bestSize ?: parameters!!.previewSize
        }

        @SuppressLint("InlinedApi")
        private fun setDesiredCameraParameters(
            camera: Camera?,
            cameraResolution: Camera.Size?,
            continuousAutoFocus: Boolean
        ) {
            val parameters = camera!!.parameters ?: return
            val supportedFocusModes = parameters
                .supportedFocusModes
            val focusMode = if (continuousAutoFocus) findValue(
                supportedFocusModes,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE,
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO,
                Camera.Parameters.FOCUS_MODE_AUTO,
                Camera.Parameters.FOCUS_MODE_MACRO
            ) else findValue(
                supportedFocusModes, Camera.Parameters.FOCUS_MODE_AUTO,
                Camera.Parameters.FOCUS_MODE_MACRO
            )
            if (focusMode != null) parameters.focusMode = focusMode
            parameters.setPreviewSize(
                cameraResolution!!.width,
                cameraResolution.height
            )
            camera.parameters = parameters
        }

        private fun getTorchEnabled(camera: Camera): Boolean {
            val parameters = camera.parameters
            if (parameters != null) {
                val flashMode = camera.parameters.flashMode
                return (flashMode != null
                        && (Camera.Parameters.FLASH_MODE_ON == flashMode || (Camera.Parameters.FLASH_MODE_TORCH
                        == flashMode)))
            }
            return false
        }

        private fun setTorchEnabled(
            camera: Camera,
            enabled: Boolean
        ) {
            val parameters = camera.parameters
            val supportedFlashModes = parameters
                .supportedFlashModes
            if (supportedFlashModes != null) {
                val flashMode: String?
                flashMode = if (enabled) findValue(
                    supportedFlashModes,
                    Camera.Parameters.FLASH_MODE_TORCH,
                    Camera.Parameters.FLASH_MODE_ON
                ) else findValue(
                    supportedFlashModes,
                    Camera.Parameters.FLASH_MODE_OFF
                )
                if (flashMode != null) {
                    camera.cancelAutoFocus() // autofocus can cause conflict
                    parameters.flashMode = flashMode
                    camera.parameters = parameters
                }
            }
        }

        private fun findValue(
            values: Collection<String>,
            vararg valuesToFind: String
        ): String? {
            for (valueToFind in valuesToFind) if (values.contains(valueToFind)) return valueToFind
            return null
        }
    }
}