package com.aihana.android.contorte

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.Button


class MainActivity : AppCompatActivity(), View.OnClickListener {
    override fun onClick(p0: View?) {
        return;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val torteButton: Button  = findViewById(R.id.torteButton) as Button
        torteButton.setOnClickListener{
            val intent = Intent(this, CallActivity::class.java)
            startActivity(intent)
        }

    }
}
