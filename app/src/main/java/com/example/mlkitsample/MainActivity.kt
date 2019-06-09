package com.example.mlkitsample
import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.ArrayAdapter
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.math.max
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentTextRecognizer
import android.content.DialogInterface
import android.Manifest.permission
import android.support.v4.app.ActivityCompat
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import com.google.android.gms.vision.face.FaceDetector.*
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions


class MainActivity : AppCompatActivity(), ImagePickFragment.ImagePickListener {

    private var bitmap: Bitmap? = null
    //許可を得られたかどうかを格納しておくメンバ変数
    private var mGetPermission: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val detectors = listOf(
            TEXT_DETECTION,
            FACE_DETECTION
        )
        detectorSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, detectors).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        detectButton.setOnClickListener {
            bitmap?.let { detect(it) }
        }

        doCheckPermission()
    }

    fun doCheckPermission(): Unit {
        //APIレベルが23未満だと、何もせず実行していい
        if (Build.VERSION.SDK_INT < 23) {
            //ここで何かしらの処理を実行して終了
            this.mGetPermission = true
            return
        }

        //すでに許可を得られていたら実行可能
        if (checkPermission()) {
            //ここで何かしらの処理を実行して終了
            this.mGetPermission = true
            return
        }

        //ダイアログが表示可能であれば使用許可を求めるダイアログを表示して返事を待つ
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            //許可待ちなのでこのメソッドは終了
            return
        }

        //ここまで来てしまった場合には許可もなくダイアログも表示できない状態
        Log.v("nullpo", "(´･ω･｀) ")
    }

    fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    override fun onImagePicked(imageUri: Uri) {
        val imageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)

        val scaleFactor = max(
            imageBitmap.width.toFloat() / imageView.width.toFloat(),
            imageBitmap.height.toFloat() / imageView.height.toFloat()
        )

        val targetWidth = (imageBitmap.width / scaleFactor).toInt()
        val targetHeight = (imageBitmap.height / scaleFactor).toInt()

        bitmap = Bitmap.createScaledBitmap(
            imageBitmap,
            targetWidth,
            targetHeight,
            true
        )

        imageView.setImageBitmap(bitmap)
        overlay.targetWidth = targetWidth
        overlay.targetHeight = targetHeight
    }

    private fun detect(bitmap: Bitmap) {

        val detectorName = detectorSpinner.selectedItem as String
        when (detectorName) {
            TEXT_DETECTION -> {
                detectButton.isEnabled = false
                val image = FirebaseVisionImage.fromBitmap(bitmap)
                val detector = FirebaseVision.getInstance()
                    .onDeviceTextRecognizer

                detector.processImage(image)
                    .addOnSuccessListener { result ->
                        detectButton.isEnabled = true
                        overlay.clear()
                        for (block in result.textBlocks) {
                            for (line in block.lines) {
                                for (element in line.elements) {
                                    overlay.add(GraphicData(
                                        element.text,
                                        element.boundingBox ?: Rect(),
                                        resources,
                                        Color.RED))
                                    Log.d("detectText", block.text)
                                }
                            }
                        }
                    }

                    .addOnFailureListener {
                        detectButton.isEnabled = true
                    }
            }

            FACE_DETECTION -> {
                detectButton.isEnabled = false
                val image = FirebaseVisionImage.fromBitmap(bitmap)

                val options = FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(ACCURATE_MODE)
                    .setLandmarkMode(ALL_LANDMARKS)
                    .setClassificationMode(ALL_CLASSIFICATIONS)
                    .setMinFaceSize(0.2f)
                    .setContourMode(ALL_CLASSIFICATIONS)
                    .build()

                FirebaseVision.getInstance()
                    .getVisionFaceDetector(options)
                    .detectInImage(image)
                    .addOnSuccessListener { faces ->
                        detectButton.isEnabled = true

                        overlay.clear()

                        for (face in faces) {
                            overlay.add(GraphicData(
                                "",
                                face.boundingBox ?: Rect(),
                                resources,
                                Color.RED))
                            Log.d("MainActivity", "${face.smilingProbability}, ${face.boundingBox}")
                        }
                    }
                    .addOnFailureListener { e ->
                        detectButton.isEnabled = true
                        e.printStackTrace()
                    }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                for (i: Int in permissions.indices) {
                    if ((permissions[i] == Manifest.permission.CAMERA) && (grantResults[i] == PackageManager.PERMISSION_GRANTED)) {
                        mGetPermission = true
                    }
                }
            }
        }
    }

    companion object {
        private const val TEXT_DETECTION = "Text"
        private const val FACE_DETECTION = "Face"
    }

}