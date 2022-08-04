package com.devfutech.videoeditorffmpeg

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.devfutech.videoeditorffmpeg.databinding.ActivityMainBinding
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }
    private var inputVideoUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupAction()
    }

    private fun setupAction() {
        var r: Runnable? = null
        binding.apply {
            btnSelectVideo.setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                intent.addCategory(Intent.CATEGORY_OPENABLE)
                intent.type = "video/*"
                getContent.launch(intent)
            }

            vvContent.setOnPreparedListener {
                val duration: Int = it.duration / 1000
                rsTimeVideo.valueTo = if (duration == 0) 1f else duration.toFloat()
                rsTimeVideo.valueFrom = 0f
                rsTimeVideo.values = listOf(0f, if (duration == 0) 1f else duration.toFloat())

                val handler = Handler(Looper.getMainLooper())
                handler.postDelayed(Runnable {
                    val isSameMaxSlider =
                        vvContent.currentPosition >= rsTimeVideo.values[1].toInt() * 1000
                    if (isSameMaxSlider) vvContent.seekTo(rsTimeVideo.values[0].toInt() * 1000)
                    handler.postDelayed(r!!, 1000)
                }.also { run -> r = run }, 1000)
            }

            rsTimeVideo.addOnChangeListener { slider, _, _ ->
                vvContent.seekTo(slider.values[0].toInt() * 1000)
            }

            btnSlow.setOnClickListener { showToast("Not available") }
            btnReverse.setOnClickListener { showToast("Not available") }
            btnFlash.setOnClickListener { showToast("Not available") }
            btnCut.setOnClickListener {
                if (inputVideoUri != null) executeCutVideoCommand(rsTimeVideo.values.first().toInt(),rsTimeVideo.values.last().toInt()) else showToast("Please upload a video")
            }
            btnCompress.setOnClickListener { showToast("Not available") }
            btnFade.setOnClickListener { showToast("Not available") }
            btnSaveVideo.setOnClickListener { if (inputVideoUri != null) createFile("VID-" + System.currentTimeMillis() / 1000) else showToast("Not available") }
        }
    }

    private fun createFile(fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "video/mp4"
        intent.putExtra(Intent.EXTRA_TITLE, fileName)
        createContent.launch(intent)
    }

    private fun executeCutVideoCommand(startMs:Int, endMs:Int) {
        val folder = cacheDir
        val file = File(folder, System.currentTimeMillis().toString() + ".mp4")
        val complexCommand = arrayOf(
            "-ss",
            "" + startMs,
            "-y",
            "-i",
            inputVideoUri!!,
            "-t",
            "" + (endMs - startMs),
            "-vcodec",
            "mpeg4",
            "-b:v",
            "2097152",
            "-b:a",
            "48000",
            "-ac",
            "2",
            "-ar",
            "22050",
            file.absolutePath
        )

        executeFfmpegCommand(complexCommand, file)
    }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result != null) {
                try {
                    inputVideoUri = FFmpegKitConfig.getSafParameterForRead(this, result.data?.data)
                    binding.vvContent.setVideoURI(result.data?.data)

                    binding.vvContent.start()
                } catch (e: Exception) {
                    showToast(e.message)
                }
            } else {
                showToast("Can't attach video")
            }
        }

    private val createContent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult? ->
            if (result != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        result.data?.data!!, Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )

                    val out = contentResolver.openOutputStream(result.data?.data!!)
                    val `in`: InputStream = FileInputStream(inputVideoUri)

                    val buffer = ByteArray(1024)
                    var read: Int
                    while (`in`.read(buffer).also { read = it } != -1) {
                        out!!.write(buffer, 0, read)
                    }
                    `in`.close()
                    out?.flush()
                    out?.close()
                } catch (e: Exception) {
                    showToast(e.message)
                }
            } else {
                showToast("Can't attach video")
            }
        }


    private fun showToast(message: String?) =
        Toast.makeText(this, message.orEmpty(), Toast.LENGTH_SHORT).show()

    private fun executeFfmpegCommand(exe: Array<String>, file: File) {
        val progressDialog = ProgressDialog(this@MainActivity)
        progressDialog.setCancelable(false)
        progressDialog.setCanceledOnTouchOutside(false)
        progressDialog.show()
        FFmpegKit.executeAsync(exe, { session ->
            val returnCode = session.returnCode
            runOnUiThread {
                if (returnCode.isSuccess) {
                    binding.vvContent.apply {
                        setVideoPath(file.absolutePath)
                        start()
                    }
                    inputVideoUri = file.absolutePath

                    progressDialog.dismiss()
                    Toast.makeText(this@MainActivity, "Filter Applied", Toast.LENGTH_SHORT).show()
                } else {
                    progressDialog.dismiss()
                    showToast("Something Went Wrong!")
                }
            }
        },
            { log ->
                Log.d("Filter",log.message)
                runOnUiThread {
                    progressDialog.setMessage(
                        """
                        Applying Filter..
                        ${log.message}
                        """.trimIndent()
                    )
                }
            }
        ) { statistics -> Log.d("STATS", statistics.toString()) }
    }

}