package dev.anonymous.yoloobjectdetector.sample

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.anonymous.yoloobjectdetector.YoloConfig
import dev.anonymous.yoloobjectdetector.YoloObjectDetector

class MainActivity : AppCompatActivity() {
    private val detector by lazy {
        YoloObjectDetector(
            context = this,
            config = YoloConfig(
                modelAssetPath = "best_int8_nms.tflite",
                labels = listOf(
                    "password_field",
                    "username_field"
                )
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<Button>(R.id.testButton).setOnClickListener {
            val bitmap = BitmapFactory.decodeResource(resources, R.drawable.test)
            val boxes = detector.detect(bitmap)
            Toast.makeText(this, "Detected ${boxes.size} objects", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detector.close()
    }
}