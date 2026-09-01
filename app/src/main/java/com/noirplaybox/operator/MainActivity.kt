package com.noirplaybox.operator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.noirplaybox.operator.ui.NoirPlayboxApp
import com.noirplaybox.operator.ui.theme.NoirPlayboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NoirPlayboxTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoirPlayboxApp()
                }
            }
        }
    }
}
