package com.mrzgaming.ezlauncher

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "EZLauncher - pilih distro (coming soon)"
        tv.textSize = 20f
        setContentView(tv)
    }
}
