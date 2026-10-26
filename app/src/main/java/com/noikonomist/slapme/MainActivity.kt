package com.noikonomist.slapme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.noikonomist.slapme.ui.home.HomeScreen
import com.noikonomist.slapme.ui.theme.SlapMeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlapMeTheme {
                HomeScreen()
            }
        }
    }
}
