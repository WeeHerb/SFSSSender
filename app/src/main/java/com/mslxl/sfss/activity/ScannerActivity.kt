package com.mslxl.sfss.activity


import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Camera
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.mslxl.sfss.util.CameraManager
import com.mslxl.sfss.R
import com.mslxl.sfss.controller.SenderAutomaton
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.*


class ScannerActivity : AppCompatActivity(), SurfaceHolder.Callback,
    CompoundButton.OnCheckedChangeListener {

    companion object{
        const val BUNDLE_IP_KEY = "ip"
        const val BUNDLE_PORT_KEY = "port"

        private const val VIBRATE_DURATION = 50L
        private const val AUTO_FOCUS_INTERVAL_MS = 2500L
    }

    private val cameraManager = CameraManager()
    private var scannerView: ScannerView? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var flOverlayContainer: FrameLayout? = null
    private var vibrator: Vibrator? = null
    private var cameraThread: HandlerThread? = null
    var cameraHandler: Handler? = null
    private var lastScanResult: String = ""

    private lateinit var sender: SenderAutomaton

    private val DISABLE_CONTINUOUS_AUTOFOCUS =
        Build.MODEL == "GT-I9100" || Build.MODEL == "SGH-T989" || Build.MODEL == "SGH-T989D" || Build.MODEL == "SAMSUNG-SGH-I727" || Build.MODEL == "GT-I9300" || Build.MODEL == "GT-N7000" // Galaxy Note

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        val ip = intent.getStringExtra(BUNDLE_IP_KEY)!!
        val port = intent.getIntExtra(BUNDLE_PORT_KEY, -1)
        sender = SenderAutomaton(ip, port){
            info->
            runOnUiThread {
                title = "${sender.statusName}: $info"
            }
        }

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        flOverlayContainer = findViewById(R.id.fl_overlay_container)
        scannerView = findViewById(R.id.scan_activity_mask)
        (findViewById<View>(R.id.cbx_torch) as CheckBox).setOnCheckedChangeListener(this)

        sender.start()
    }

    override fun onResume() {
        super.onResume()
        cameraThread = HandlerThread("cameraThread", Process.THREAD_PRIORITY_BACKGROUND)
        cameraThread!!.start()
        cameraHandler = Handler(cameraThread!!.looper)
        val surfaceView = findViewById<View>(R.id.scan_activity_preview) as SurfaceView
        surfaceHolder = surfaceView.holder
        surfaceHolder!!.addCallback(this)
        surfaceHolder!!.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        cameraHandler!!.post(openRunnable)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun onPause() {
        cameraHandler!!.post(closeRunnable)
        surfaceHolder!!.removeCallback(this)
        super.onPause()
    }

    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_FOCUS, KeyEvent.KEYCODE_CAMERA ->                 // don't launch camera app
                return true
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP -> {
                cameraHandler!!.post { cameraManager.setTorch(keyCode == KeyEvent.KEYCODE_VOLUME_UP) }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
    fun handleResult(
        scanResult: Result
    ) {
//    fun handleResult(
//        scanResult: Result,
//        thumbnailImage: Bitmap,
//        thumbnailScaleFactor: Float
//    ) {

        if(scanResult.text != lastScanResult){
            vibrate()
            Toast.makeText(this, scanResult.text, Toast.LENGTH_SHORT).show()
            sender.sendQR(scanResult.text)
            lastScanResult = scanResult.text
        }
//        var thumbnailImage = thumbnailImage
        // superimpose dots to highlight the key features of the qr code
//        val points: Array<ResultPoint> = scanResult.getResultPoints()
//        if (points.isNotEmpty()) {
//            val paint = Paint()
//            paint.color = resources.getColor(R.color.scan_result_dots)
//            paint.strokeWidth = 10.0f
//            val canvas = Canvas(thumbnailImage)
//            canvas.scale(thumbnailScaleFactor, thumbnailScaleFactor)
//            for (point in points) canvas.drawPoint(point.x, point.y, paint)
//        }
//        val matrix = Matrix()
//        matrix.postRotate(90F)
//        thumbnailImage = Bitmap.createBitmap(
//            thumbnailImage, 0, 0,
//            thumbnailImage.width, thumbnailImage.height, matrix,
//            false
//        )
//        scannerView!!.drawResultBitmap(thumbnailImage)


    }

    private fun vibrate() {
        vibrator!!.vibrate(VIBRATE_DURATION)
    }

    private val openRunnable = Runnable {
        try {
            val camera: Camera? = cameraManager.open(
                surfaceHolder!!,
                !DISABLE_CONTINUOUS_AUTOFOCUS
            )
            val framingRect: Rect? = cameraManager.frame
            val framingRectInPreview: Rect? = cameraManager
                .framePreview
            runOnUiThread {
                scannerView!!.setFraming(
                    framingRect,
                    framingRectInPreview
                )
            }
            val focusMode: String = camera!!.getParameters().getFocusMode()
            val nonContinuousAutoFocus = (Camera.Parameters.FOCUS_MODE_AUTO == focusMode
                    || Camera.Parameters.FOCUS_MODE_MACRO == focusMode)
            if (nonContinuousAutoFocus) cameraHandler!!.post(AutoFocusRunnable(camera))
            cameraHandler!!.post(fetchAndDecodeRunnable)
        } catch (x: IOException) {
            Log.i("problem opening camera", x.toString())
            finish()
        } catch (x: RuntimeException) {
            Log.i("problem opening camera", x.toString())
            finish()
        }
    }

    private val closeRunnable = Runnable {
        cameraManager.close()
        cameraHandler!!.removeCallbacksAndMessages(null)
        cameraThread!!.quit()
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView.id == R.id.cbx_torch) {
            if (cameraHandler == null) {
                return
            }
            cameraHandler!!.post(Runnable {
                cameraManager
                    .setTorch(isChecked)
            })
        }
    }

    private inner class AutoFocusRunnable(camera: Camera?) : Runnable {
        private val camera: Camera?

        init {
            this.camera = camera
        }

        override fun run() {
            camera!!.autoFocus { _, _ -> // schedule again
                cameraHandler!!.postDelayed(
                    this@AutoFocusRunnable,
                    AUTO_FOCUS_INTERVAL_MS
                )
            }
        }
    }

    private val fetchAndDecodeRunnable: Runnable = object : Runnable {
        private val reader = QRCodeReader()
        private val hints: MutableMap<DecodeHintType, Any?> = EnumMap<DecodeHintType, Any>(
            DecodeHintType::class.java
        )

        override fun run() {
            cameraHandler!!.postDelayed(this, 500)
            cameraManager.requestPreviewFrame { data, _ -> decode(data) }
        }

        private fun decode(data: ByteArray) {
            val source = cameraManager.buildLuminanceSource(data)
            val bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                hints[DecodeHintType.NEED_RESULT_POINT_CALLBACK] =
                    ResultPointCallback { dot -> runOnUiThread { scannerView!!.addDot(dot) } }
                val scanResult: Result = reader.decode(bitmap, hints)
                if (!resultValid(scanResult.text)) {
                    cameraHandler!!.post(this)
                    return
                }
//                val thumbnailWidth = source.thumbnailWidth
//                val thumbnailHeight = source.thumbnailHeight
//                val thumbnailScaleFactor = thumbnailWidth.toFloat() / source.width
//                val thumbnailImage = Bitmap.createBitmap(
//                    thumbnailWidth, thumbnailHeight,
//                    Bitmap.Config.ARGB_8888
//                )
//                thumbnailImage.setPixels(
//                    source.renderThumbnail(), 0,
//                    thumbnailWidth, 0, 0, thumbnailWidth, thumbnailHeight
//                )

//                runOnUiThread {
//                    handleResult(
//                        scanResult, thumbnailImage,
//                        thumbnailScaleFactor
//                    )
//                }
                runOnUiThread {
                    handleResult(
                        scanResult
                    )
                }
            } catch (x: Exception) {
                cameraHandler!!.post(this)
            } finally {
                reader.reset()
            }
        }
    }

    fun resultValid(result: String?): Boolean {
        return true
    }

    fun startScan() {
        cameraHandler!!.post(fetchAndDecodeRunnable)
    }

    private fun decodeQrCodeFromBitmap(bmp: Bitmap): String? {
        var bmp: Bitmap? = bmp
        val width = bmp!!.width
        val height = bmp.height
        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)
        bmp.recycle()
        bmp = null
        val reader = QRCodeReader()
        val hints: MutableMap<DecodeHintType, Any?> = EnumMap<DecodeHintType, Any>(
            DecodeHintType::class.java
        )
        hints[DecodeHintType.TRY_HARDER] = java.lang.Boolean.TRUE
        try {
            val result: Result = reader.decode(
                BinaryBitmap(
                    HybridBinarizer(
                        RGBLuminanceSource(
                            width,
                            height,
                            pixels
                        )
                    )
                ), hints
            )
            return result.getText()
        } catch (e: NotFoundException) {
            e.printStackTrace()
        } catch (e: ChecksumException) {
            e.printStackTrace()
        } catch (e: FormatException) {
            e.printStackTrace()
        }
        return null
    }

    fun getBitmapNearestSize(file: File?, size: Int): Bitmap? {
        return try {
            if (file == null || !file.exists()) {
                return null
            } else if (file.length() == 0L) {
                file.delete()
                return null
            }
            val opts = BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts)
            val sampleSize = getSampleSize(
                Math.min(opts.outHeight, opts.outWidth), size
            )
            opts.inSampleSize = sampleSize
            opts.inJustDecodeBounds = false
            opts.inPurgeable = true
            opts.inInputShareable = false
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getSampleSize(fileSize: Int, targetSize: Int): Int {
        var sampleSize = 1
        if (fileSize > targetSize * 2) {
            var sampleLessThanSize = 0
            do {
                sampleLessThanSize++
            } while (fileSize / sampleLessThanSize > targetSize)
            for (i in 1..sampleLessThanSize) {
                if (Math.abs(fileSize / i - targetSize) <= Math.abs(
                        fileSize
                                / sampleSize - targetSize
                    )
                ) {
                    sampleSize = i
                }
            }
        } else {
            sampleSize = if (fileSize <= targetSize) {
                1
            } else {
                2
            }
        }
        return sampleSize
    }

    fun convertUriToFile(activity: Activity, uri: Uri): File? {
        var file: File? = null
        try {
            val proj = arrayOf(MediaStore.Images.Media.DATA)
            val actualimagecursor: Cursor? = activity.managedQuery(
                uri, proj, null,
                null, null
            )
            if (actualimagecursor != null) {
                val actual_image_column_index: Int = actualimagecursor
                    .getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                actualimagecursor.moveToFirst()
                val img_path: String = actualimagecursor
                    .getString(actual_image_column_index)
                if (!isEmpty(img_path)) {
                    file = File(img_path)
                }
            } else {
                file = File(URI(uri.toString()))
                if (file.exists()) {
                    return file
                }
            }
        } catch (e: Exception) {
        }
        return file
    }

    fun isEmpty(str: String?): Boolean {
        return str == null || str == ""
    }
}