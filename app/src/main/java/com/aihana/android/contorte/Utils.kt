package com.aihana.android.contorte

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Webrtc_Step3
 * Created by vivek-3102 on 11/03/17.
 */

class Utils {

    var retrofitInstance: Retrofit? = null

    internal fun getRetrofitInstance(): TurnServer {
        if (retrofitInstance == null) {
            retrofitInstance = Retrofit.Builder()
                    .baseUrl(API_ENDPOINT)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
        }
        return retrofitInstance!!.create(TurnServer::class.java)
    }

    companion object {

        internal var instance: Utils? = null
        val API_ENDPOINT = "https://service.xirsys.com"

        fun getInstance(): Utils {
            if (instance == null) {
                instance = Utils()
            }
            return instance as Utils
        }
    }
}