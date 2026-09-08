package com.superdriver.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.superdriver.app.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        runCatching {
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(DARK_SYSTEM_BAR_COLOR)
            )
        }
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var initError by remember { mutableStateOf<String?>(null) }

                if (initError != null) {
                    ErrorFallbackScreen(error = initError!!)
                } else {
                    SafeMainScreen(onError = { error -> initError = error })
                }
            }
        }
    }

    private companion object {
        const val DARK_SYSTEM_BAR_COLOR = 0xFF0B0F14.toInt()
    }
}

@Composable
private fun SafeMainScreen(onError: (String) -> Unit) {
    runCatching {
        MainScreen()
    }.onFailure { error ->
        onError(error.message ?: error.toString())
    }
}

@Composable
private fun ErrorFallbackScreen(error: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color(0xFF0B0F14)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "سوبر درايفر 🇪🇬",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF65D6AD)
            )
            Text(
                text = "حدث خطأ أثناء تشغيل التطبيق:",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = androidx.compose.ui.graphics.Color.White
            )
            Text(
                text = error,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color(0xFFFF8A9A)
            )
        }
    }
}
