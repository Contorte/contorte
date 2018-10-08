package com.aihana.android.contorte
import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import org.webrtc.PeerConnection.IceServer

class TurnServerPojo {

    @SerializedName("s")
    @Expose
    var s: Int? = null
    @SerializedName("p")
    @Expose
    var p: String? = null
    @SerializedName("e")
    @Expose
    var e: Any? = null
    @SerializedName("d")
    @Expose
    var iceServerList: IceServerList? = null

    internal inner class IceServerList {

        @SerializedName("iceServers")
        @Expose
        var iceServers: List<IceServer>? = null

    }
}