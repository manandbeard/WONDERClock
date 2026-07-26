package com.manandbeard.wonderclock.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.manandbeard.wonderclock.ui.screens.HomeScreen
import com.manandbeard.wonderclock.ui.theme.WonderClockTheme
import com.manandbeard.wonderclock.widget.TapActions

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WonderClockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HomeScreen(
                        onEditWidget = { widgetId ->
                            startActivity(TapActions.configureIntent(this, widgetId))
                        },
                    )
                }
            }
        }
    }
}
