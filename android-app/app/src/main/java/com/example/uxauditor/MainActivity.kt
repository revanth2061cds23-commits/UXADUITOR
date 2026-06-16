package com.example.uxauditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.example.uxauditor.theme.UXAuditorTheme

class MainActivity : ComponentActivity() {
  private val sharedUris = mutableStateOf<List<Uri>>(emptyList())

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      UXAuditorTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(sharedUris = sharedUris)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    val action = intent.action
    val type = intent.type
    if (type?.startsWith("image/") == true) {
      if (Intent.ACTION_SEND == action) {
        val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (uri != null) {
          sharedUris.value = listOf(uri)
        }
      } else if (Intent.ACTION_SEND_MULTIPLE == action) {
        val uris = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
        }
        if (uris != null) {
          sharedUris.value = uris.filterNotNull()
        }
      }
      // Consume the intent so it doesn't fire again on rotation
      intent.action = null
      intent.removeExtra(Intent.EXTRA_STREAM)
    }
  }
}
