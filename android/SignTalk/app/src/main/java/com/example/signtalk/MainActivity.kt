package com.example.signtalk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.signtalk.ui.navigation.SignTalkNavGraph
import com.example.signtalk.ui.theme.SignTalkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as SignTalkApp).container

        setContent {
            SignTalkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SignTalkNavGraph(
                        appContainer = appContainer,
                        applicationForViewModels = application
                    )
                }
            }
        }
    }
}
