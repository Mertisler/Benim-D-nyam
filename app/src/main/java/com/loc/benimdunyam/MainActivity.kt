package com.loc.benimdunyam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.loc.benimdunyam.core.designsystem.BenimDunyamTheme
import com.loc.benimdunyam.core.navigation.BenimDunyamNavGraph
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BenimDunyamTheme {
                BenimDunyamNavGraph()
            }
        }
    }
}