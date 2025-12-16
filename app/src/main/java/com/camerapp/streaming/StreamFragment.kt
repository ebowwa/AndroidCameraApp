package com.camerapp.streaming

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioFormat
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.Surface
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.camerapp.R
import com.camerapp.databinding.FragmentStreamBinding
import io.github.thibaultbee.streampack.core.configuration.mediadescriptor.UriMediaDescriptor
import io.github.thibaultbee.streampack.core.interfaces.IWithVideoSource
import io.github.thibaultbee.streampack.core.interfaces.startPreview
import io.github.thibaultbee.streampack.core.interfaces.startStream
import io.github.thibaultbee.streampack.core.streamers.single.AudioConfig
import io.github.thibaultbee.streampack.core.streamers.single.VideoConfig
import io.github.thibaultbee.streampack.core.streamers.single.cameraSingleStreamer
import io.github.thibaultbee.streampack.core.streamers.single.SingleStreamer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for RTMP live streaming using StreamPack library.
 * Streams camera + audio to an RTMP server for viewing on iPhone or other devices.
 */
class StreamFragment : Fragment() {

    private var _binding: FragmentStreamBinding? = null
    private val binding get() = _binding!!

    private var streamer: SingleStreamer? = null
    private var isStreaming = false
    private var blinkAnimation: Animation? = null

    private val prefs: SharedPreferences by lazy {
        requireContext().getSharedPreferences("stream_settings", Context.MODE_PRIVATE)
    }

    // Default RTMP URL - user can change this
    private var rtmpUrl: String
        get() = prefs.getString(PREF_RTMP_URL, DEFAULT_RTMP_URL) ?: DEFAULT_RTMP_URL
        set(value) = prefs.edit().putString(PREF_RTMP_URL, value).apply()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        setupSurfaceView()
    }

    private fun setupUI() {
        // Update RTMP URL display
        updateRtmpUrlDisplay()

        // Stream button
        binding.streamButton.setOnClickListener {
            if (isStreaming) {
                stopStream()
            } else {
                startStream()
            }
        }

        // RTMP URL click to edit
        binding.rtmpUrlText.setOnClickListener {
            showRtmpUrlDialog()
        }

        // Settings button
        binding.settingsButton.setOnClickListener {
            showRtmpUrlDialog()
        }

        // Camera mode button - switch to CameraFragment
        binding.cameraModeButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Setup blink animation for live indicator
        blinkAnimation = AlphaAnimation(1.0f, 0.0f).apply {
            duration = 500
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        }
    }

    private fun setupSurfaceView() {
        binding.previewView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Log.d(TAG, "Surface created")
                initializeStreamer(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed: ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Log.d(TAG, "Surface destroyed")
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun initializeStreamer(surface: Surface) {
        lifecycleScope.launch {
            try {
                // Create camera streamer
                streamer = cameraSingleStreamer(context = requireContext())

                // Configure audio
                val audioConfig = AudioConfig(
                    startBitrate = 128000,
                    sampleRate = 44100,
                    channelConfig = AudioFormat.CHANNEL_IN_STEREO
                )

                // Configure video - 720p at 30fps, 2 Mbps
                val videoConfig = VideoConfig(
                    startBitrate = 2000000,
                    resolution = Size(1280, 720),
                    fps = 30
                )

                streamer?.let { s ->
                    s.setAudioConfig(audioConfig)
                    s.setVideoConfig(videoConfig)

                    // Set orientation
                    s.setTargetRotation(Surface.ROTATION_0)

                    // Start preview on the surface using extension function
                    (s as? IWithVideoSource)?.startPreview(surface)

                    Log.d(TAG, "Streamer initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize streamer", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireContext(),
                        "Failed to initialize camera: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun startStream() {
        val url = rtmpUrl
        if (url.isBlank() || url == DEFAULT_RTMP_URL) {
            Toast.makeText(requireContext(), "Please set RTMP URL first", Toast.LENGTH_SHORT).show()
            showRtmpUrlDialog()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting stream to: $url")

                streamer?.startStream(UriMediaDescriptor(url))

                isStreaming = true
                withContext(Dispatchers.Main) {
                    updateStreamingUI(true)
                    Toast.makeText(requireContext(), "Streaming started", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "Stream started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start stream", e)
                isStreaming = false
                withContext(Dispatchers.Main) {
                    updateStreamingUI(false)
                    Toast.makeText(
                        requireContext(),
                        "Failed to start stream: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun stopStream() {
        lifecycleScope.launch {
            try {
                streamer?.stopStream()
                isStreaming = false

                withContext(Dispatchers.Main) {
                    updateStreamingUI(false)
                    Toast.makeText(requireContext(), "Stream stopped", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "Stream stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping stream", e)
            }
        }
    }

    private fun updateStreamingUI(streaming: Boolean) {
        if (streaming) {
            // Show live indicator
            binding.statusOverlay.visibility = View.VISIBLE
            binding.liveIndicator.startAnimation(blinkAnimation)
            binding.statusText.text = "LIVE"

            // Change button color to green (streaming)
            binding.streamButton.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.stream_button_streaming)
        } else {
            // Hide live indicator
            binding.statusOverlay.visibility = View.GONE
            binding.liveIndicator.clearAnimation()

            // Change button color back to red
            binding.streamButton.backgroundTintList =
                ContextCompat.getColorStateList(requireContext(), R.color.stream_button_color)
        }
    }

    private fun updateRtmpUrlDisplay() {
        val url = rtmpUrl
        binding.rtmpUrlText.text = if (url == DEFAULT_RTMP_URL) {
            "Tap to set RTMP URL"
        } else {
            url
        }
    }

    private fun showRtmpUrlDialog() {
        val editText = EditText(requireContext()).apply {
            setText(if (rtmpUrl == DEFAULT_RTMP_URL) "" else rtmpUrl)
            hint = "rtmp://192.168.1.100:1935/live/stream"
            setSingleLine()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("RTMP Server URL")
            .setMessage("Enter the RTMP URL to stream to.\n\nFor testing, run on your Mac:\nffplay -listen 1 -i 'rtmp://0.0.0.0:1935/live/stream'")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotBlank()) {
                    rtmpUrl = url
                    updateRtmpUrlDisplay()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        if (isStreaming) {
            stopStream()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        lifecycleScope.launch {
            try {
                streamer?.stopStream()
                streamer?.close()
                streamer?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing streamer", e)
            }
        }
        _binding = null
    }

    companion object {
        private const val TAG = "StreamFragment"
        private const val PREF_RTMP_URL = "rtmp_url"
        private const val DEFAULT_RTMP_URL = "rtmp://localhost:1935/live/stream"
    }
}
