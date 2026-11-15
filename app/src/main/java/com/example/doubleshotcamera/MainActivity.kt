package com.example.doubleshotcamera

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private lateinit var preview: PreviewView
    private lateinit var status: TextView
    private lateinit var capture: Button
    private lateinit var switch: Button

    private val executor = Executors.newSingleThreadExecutor()

    private var imageCapture: ImageCapture? = null
    private var lens = CameraSelector.LENS_FACING_BACK
    private var firstPhoto: Bitmap? = null
    private var firstStep = true

    private val permission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            if (it) startCamera()
            else status.text = "Camera permission required"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(0xFF101820.toInt())
        }

        val title = TextView(this).apply {
            text = "DOUBLE SHOT CAMERA"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 15, 0, 15)
        }

        status = TextView(this).apply {
            text = "Photo 1 of 2: Capture first photo"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 12, 0, 12)
        }

        preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        capture = Button(this).apply {
            text = "CAPTURE FIRST PHOTO"
            setOnClickListener { takePhoto() }
        }

        switch = Button(this).apply {
            text = "SWITCH FRONT / BACK"
            setOnClickListener {
                lens =
                    if (lens == CameraSelector.LENS_FACING_BACK)
                        CameraSelector.LENS_FACING_FRONT
                    else
                        CameraSelector.LENS_FACING_BACK

                startCamera()
            }
        }

        val reset = Button(this).apply {
            text = "START OVER"
            setOnClickListener { resetCamera() }
        }

        root.addView(title)
        root.addView(status)
        root.addView(
            preview,
            LinearLayout.LayoutParams(
                -1, 0, 1f
            )
        )
        root.addView(capture)
        root.addView(switch)
        root.addView(reset)

        setContentView(root)

        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            val provider = future.get()

            val cameraPreview = Preview.Builder().build()
            cameraPreview.setSurfaceProvider(preview.surfaceProvider)

            val photoCapture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                )
                .build()

            imageCapture = photoCapture

            val selector = CameraSelector.Builder()
                .requireLensFacing(lens)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    selector,
                    cameraPreview,
                    photoCapture
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this, "Camera not available",
                    Toast.LENGTH_SHORT
                ).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val camera = imageCapture ?: return

        capture.isEnabled = false
        capture.text = "CAPTURING..."

        val file = File(
            cacheDir,
            "photo_${System.currentTimeMillis()}.jpg"
        )

        val options = ImageCapture.OutputFileOptions
            .Builder(file)
            .build()

        camera.takePicture(
            options,
            executor,
            object : ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    val bitmap = BitmapFactory.decodeFile(
                        file.absolutePath
                    )

                    runOnUiThread {
                        if (bitmap == null) {
                            capture.isEnabled = true
                            capture.text = "TRY AGAIN"
                            return@runOnUiThread
                        }

                        if (firstStep) {
                            firstPhoto = bitmap
                            firstStep = false

                            status.text =
                                "First photo captured! Take second photo."

                            capture.text = "CAPTURE SECOND PHOTO"
                            capture.isEnabled = true

                        } else {
                            val first = firstPhoto

                            if (first != null) {
                                status.text = "Combining photos..."
                                combinePhotos(first, bitmap)
                            }
                        }
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    runOnUiThread {
                        capture.isEnabled = true
                        capture.text =
                            if (firstStep)
                                "CAPTURE FIRST PHOTO"
                            else
                                "CAPTURE SECOND PHOTO"

                        Toast.makeText(
                            this@MainActivity,
                            "Photo capture failed",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun combinePhotos(
        first: Bitmap,
        second: Bitmap
    ) {
        executor.execute {
            try {
                val height = max(first.height, second.height)

                val width1 =
                    (first.width.toLong() * height / first.height).toInt()

                val width2 =
                    (second.width.toLong() * height / second.height).toInt()

                val combined = Bitmap.createBitmap(
                    width1 + width2,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                val canvas = Canvas(combined)
                val paint = Paint(
                    Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
                )

                canvas.drawBitmap(
                    first, null,
                    Rect(0, 0, width1, height),
                    paint
                )

                canvas.drawBitmap(
                    second, null,
                    Rect(width1, 0, width1 + width2, height),
                    paint
                )

                val filename =
                    "DoubleShot_" +
                    SimpleDateFormat(
                        "yyyyMMdd_HHmmss",
                        Locale.US
                    ).format(Date()) + ".jpg"

                val values = ContentValues().apply {
                    put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        filename
                    )
                    put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                    )

                    if (Build.VERSION.SDK_INT >= 29) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/DoubleShotCamera"
                        )
                        put(
                            MediaStore.Images.Media.IS_PENDING,
                            1
                        )
                    }
                }

                val uri = contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                )

                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use {
                        combined.compress(
                            Bitmap.CompressFormat.JPEG,
                            95,
                            it
                        )
                    }

                    if (Build.VERSION.SDK_INT >= 29) {
                        val done = ContentValues().apply {
                            put(
                                MediaStore.Images.Media.IS_PENDING,
                                0
                            )
                        }
                        contentResolver.update(
                            uri, done, null, null
                        )
                    }

                    runOnUiThread {
                        status.text =
                            "SUCCESS! Photo saved in Gallery."

                        capture.text = "TAKE ANOTHER PHOTO"
                        capture.isEnabled = true
                        capture.setOnClickListener {
                            resetCamera()
                        }

                        Toast.makeText(
                            this,
                            "Saved in Pictures/DoubleShotCamera",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            "Could not save photo",
                            Toast.LENGTH_SHORT
                        ).show()
                        resetCamera()
                    }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Error combining photos",
                        Toast.LENGTH_LONG
                    ).show()
                    resetCamera()
                }
            }
        }
    }

    private fun resetCamera() {
        firstPhoto = null
        firstStep = true

        status.text = "Photo 1 of 2: Capture first photo"
        capture.text = "CAPTURE FIRST PHOTO"
        capture.isEnabled = true
        capture.setOnClickListener { takePhoto() }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}

