package com.aihana.android.contorte

import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.Window
import android.view.Window.FEATURE_NO_TITLE



class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        // タイトルを非表示にします。
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        // splash.xmlをViewに指定します。
        setContentView(R.layout.activity_splash)
        Handler().postDelayed(Runnable {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }, 500)
    }

}
