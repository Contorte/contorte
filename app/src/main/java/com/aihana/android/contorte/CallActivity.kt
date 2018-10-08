package com.aihana.android.contorte

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.telecom.Call
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.*
import android.view.ViewGroup.LayoutParams.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import org.webrtc.Camera1Enumerator
import org.webrtc.VideoCapturer
import org.webrtc.CameraEnumerator
import org.webrtc.VideoTrack
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.EglBase
import org.webrtc.SurfaceViewRenderer


class CallActivity : AppCompatActivity(), View.OnClickListener, SignallingClientKotlin.SignalingInterface {

    private val rootEglBase by lazy { EglBase.create() }

    private val peerConnectionFactory: PeerConnectionFactory by lazy {
        //Initialize PeerConnectionFactory globals.
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableVideoHwAcceleration(true)
                .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(
                rootEglBase.eglBaseContext, /* enableIntelVp8Encoder */true, /* enableH264HighProfile */true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(rootEglBase.eglBaseContext)
        PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory)
    }

    private var sdpConstraints: MediaConstraints? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null


    private var localVideoView: SurfaceViewRenderer? = null
    private var remoteVideoView: SurfaceViewRenderer? = null

    private var hangup: Button? = null
    private var localPeer: PeerConnection? = null

    private var gotUserMedia: Boolean = false
    private var peerIceServers: MutableList<PeerConnection.IceServer> = ArrayList()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initVideos()
        getIceServers()
        SignallingClientKotlin.init(this)
        start()
    }

    private fun initViews() {
        hangup = findViewById(R.id.end_call)
        localVideoView = findViewById(R.id.local_gl_surface_view)
        remoteVideoView = findViewById(R.id.remote_gl_surface_view)
        hangup?.setOnClickListener(this)
    }

    private fun initVideos() {
        localVideoView?.init(rootEglBase.eglBaseContext, null)
        remoteVideoView?.init(rootEglBase.eglBaseContext, null)
        localVideoView?.setZOrderMediaOverlay(true)
        remoteVideoView?.setZOrderMediaOverlay(true)
    }

    private fun getIceServers() {
        //get Ice servers using xirsys
        Utils.getInstance().retrofitInstance.iceCandidates.enqueue(object : Callback<TurnServerPojo> {
            override fun onResponse(call: Call<TurnServerPojo>, response: Response<TurnServerPojo>) {
                var iceServers: List<IceServer> = ArrayList()
                val body = response.body()
                if (body != null) {
                    iceServers = body.iceServerList.iceServers
                }
                for (iceServer in iceServers) {
                    if (iceServer.credential == null) {
                        val peerIceServer = PeerConnection.IceServer.builder(iceServer.url).createIceServer()
                        peerIceServers.add(peerIceServer)
                    } else {
                        val peerIceServer = PeerConnection.IceServer.builder(iceServer.url)
                                .setUsername(iceServer.username)
                                .setPassword(iceServer.credential)
                                .createIceServer()
                        peerIceServers.add(peerIceServer)
                    }
                }
                Log.d("onApiResponse", "IceServers\n" + iceServers.toString())
            }

            override fun onFailure(call: Call<TurnServerPojo>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun start() {


        //Now create a VideoCapturer instance.
        val videoCapturerAndroid: VideoCapturer? = createCameraCapturer(Camera1Enumerator(false))

        val audioConstraints = MediaConstraints()

        //Create MediaConstraints - Will be useful for specifying video and audio constraints.

        val videoSource: VideoSource

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid)
            localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource)
        }

        //create an AudioSource instance
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource)


        videoCapturerAndroid?.startCapture(1024, 720, 30)
        localVideoView?.visibility = View.VISIBLE
        //create a videoRenderer based on SurfaceViewRenderer instance
        val localRenderer = VideoRenderer(localVideoView)
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack?.addRenderer(localRenderer)

        localVideoView?.setMirror(true)
        remoteVideoView?.setMirror(true)

        gotUserMedia = true
        if (SignallingClientKotlin.isInitiator) {
            onTryToStart()
        }
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    override fun onTryToStart() {
        runOnUiThread {
            if (!SignallingClientKotlin.isStarted && localVideoTrack != null && SignallingClientKotlin.isChannelReady) {
                createPeerConnection()
                SignallingClientKotlin.isStarted = true
                if (SignallingClientKotlin.isInitiator) {
                    doCall()
                }
            }
        }
    }


    /**
     * Creating the local peerconnection instance
     */
    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(peerIceServers)
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, object : CustomPeerConnectionObserver("localPeerCreation") {
            override fun onIceCandidate(iceCandidate: IceCandidate) {
                super.onIceCandidate(iceCandidate)
                onIceCandidateReceived(iceCandidate)
            }

            override fun onAddStream(mediaStream: MediaStream) {
                showToast("Received Remote stream")
                super.onAddStream(mediaStream)
                gotRemoteStream(mediaStream)
            }
        })

        addStreamToLocalPeer()
    }

    /**
     * Adding the stream to the localpeer
     */
    private fun addStreamToLocalPeer() {
        //creating local mediastream
        val stream = peerConnectionFactory.createLocalMediaStream("102")
        stream.addTrack(localAudioTrack)
        stream.addTrack(localVideoTrack)
        localPeer!!.addStream(stream)
    }

    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private fun doCall() {
        localPeer!!.createOffer(object : CustomSdpObserver("localCreateOffer") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(CustomSdpObserver("localSetLocalDesc"), sessionDescription)
                Log.d("onCreateSuccess", "SignallingClient emit ")
                SignallingClientKotlin.emitMessage(sessionDescription)
            }
        }, sdpConstraints)
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private fun gotRemoteStream(stream: MediaStream) {
        //we have remote video stream. add to the renderer.
        val videoTrack = stream.videoTracks[0]
        runOnUiThread {
            try {
                val remoteRenderer = VideoRenderer(remoteVideoView)
                remoteVideoView?.visibility = View.VISIBLE
                videoTrack.addRenderer(remoteRenderer)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }


    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    fun onIceCandidateReceived(iceCandidate: IceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        SignallingClientKotlin.emitIceCandidate(iceCandidate)
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    override fun onCreatedRoom() {
        showToast("You created the room " + gotUserMedia)
        if (gotUserMedia) {
            SignallingClientKotlin.emitMessage("got user media")
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    override fun onJoinedRoom() {
        showToast("You joined the room " + gotUserMedia)
        if (gotUserMedia) {
            SignallingClientKotlin.emitMessage("got user media")
        }
    }

    override fun onNewPeerJoined() {
        showToast("Remote Peer Joined")
    }

    override fun onRemoteHangUp(msg: String) {
        showToast("Remote Peer hungup")
        runOnUiThread({ this.hangup() })
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    override fun onOfferReceived(data: JSONObject) {
        showToast("Received Offer")
        runOnUiThread {
            if (!SignallingClientKotlin.isInitiator && !SignallingClientKotlin.isStarted) {
                onTryToStart()
            }
            try {
                localPeer!!.setRemoteDescription(CustomSdpObserver("localSetRemote"), SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")))
                doAnswer()
                updateVideoViews(true)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
    }

    private fun doAnswer() {
        localPeer!!.createAnswer(object : CustomSdpObserver("localCreateAns") {
            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                super.onCreateSuccess(sessionDescription)
                localPeer!!.setLocalDescription(CustomSdpObserver("localSetLocal"), sessionDescription)
                SignallingClientKotlin.emitMessage(sessionDescription)
            }
        }, MediaConstraints())
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    override fun onAnswerReceived(data: JSONObject) {
        showToast("Received Answer")
        try {
            localPeer!!.setRemoteDescription(CustomSdpObserver("localSetRemote"), SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")))
            updateVideoViews(true)
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    /**
     * Remote IceCandidate received
     */
    override fun onIceCandidateReceived(data: JSONObject) {
        try {
            localPeer!!.addIceCandidate(IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

    }

    private fun updateVideoViews(remoteVisible: Boolean) {
        runOnUiThread {
            var params = localVideoView?.layoutParams
            params?.let {
                if (remoteVisible) {
                    it.height = dpToPx(100)
                    it.width = dpToPx(100)
                } else {
                    params = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
                }
                localVideoView?.layoutParams = params
            }
        }
    }

    /**
     * Closing up - normal hangup and app destroye
     */

    override fun onClick(v: View) {
        when (v.id) {
            R.id.end_call -> {
                hangup()
            }
        }
    }

    private fun hangup() {
        try {
            localPeer!!.close()
            localPeer = null
            SignallingClientKotlin.close()
            updateVideoViews(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    override fun onDestroy() {
        SignallingClientKotlin.close()
        super.onDestroy()
    }

    /**
     * Util Methods
     */
    private fun dpToPx(dp: Int): Int {
        val displayMetrics = resources.displayMetrics
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT))
    }

    private fun showToast(msg: String) {
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    private fun createCameraCapturer(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        //find the front facing camera and return it.
        deviceNames
                .filter { enumerator.isFrontFacing(it) }
                .mapNotNull { enumerator.createCapturer(it, null) }
                .forEach { return it }

        return null
    }
}
