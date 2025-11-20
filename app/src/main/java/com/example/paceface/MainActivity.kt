package com.example.paceface

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // ★ 最初に SplashScreen をセット
        installSplashScreen()

        super.onCreate(savedInstanceState)

        // LoginActivity を起動して、MainActivity は終了する
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
