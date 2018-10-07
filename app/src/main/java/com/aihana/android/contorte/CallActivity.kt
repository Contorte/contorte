package com.aihana.android.contorte

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_call.*
import org.webrtc.*
import org.webrtc.Camera1Enumerator
import org.webrtc.VideoCapturer
import org.webrtc.CameraEnumerator
import org.webrtc.VideoTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.VideoRenderer
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer












class CallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        PeerConnectionFactory.initializeAndroidGlobals(this, true, true, true)

        //Create a new PeerConnectionFactory instance.
        val options = PeerConnectionFactory.Options()
        val peerConnectionFactory = PeerConnectionFactory(options)


        //Now create a VideoCapturer instance. Callback methods are there if you want to do something! Duh!
        val videoCapturerAndroid = createVideoCapturer()
        //Create MediaConstraints - Will be useful for specifying video and audio constraints. More on this later!
        val constraints = MediaConstraints()

        //Create a VideoSource instance
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid)
        val localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)

        //create an AudioSource instance
        val audioSource = peerConnectionFactory.createAudioSource(constraints)
        val localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)

        //we will start capturing the video from the camera
        //params are width,height and fps
        videoCapturerAndroid?.startCapture(1000, 1000, 30)

        //create surface renderer, init it and add the renderer to the track
        val videoView = findViewById(R.id.surface_rendeer) as SurfaceViewRenderer
        videoView.setMirror(true)

        val rootEglBase = EglBase.create()
        videoView.init(rootEglBase.eglBaseContext, null)

        localVideoTrack.addRenderer(VideoRenderer(videoView))


        callButton.setOnClickListener {
            callButton.visibility = View.INVISIBLE
            calloffButton.visibility = View.VISIBLE
        }
        calloffButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }


    }

    private fun createVideoCapturer(): VideoCapturer? {
        val videoCapturer : VideoCapturer?  = createCameraCapturer(Camera1Enumerator(false))
        return videoCapturer
    }


    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames

        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapturer : VideoCapturer? = enumerator.createCapturer(deviceName, null)

                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }

        // We were not able to find a front cam. Look for other cameras
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapturer : VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapturer != null) {
                    return videoCapturer
                }
            }
        }
        return null
    }
}
