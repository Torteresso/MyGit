package com.example.android_app

import android.os.Bundle
import android.os.storage.StorageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.getSystemService
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(

            ) {
                val storageManager = applicationContext.getSystemService<StorageManager>()!!
                val appSpecificInternalDirUuid: UUID = storageManager.getUuidForPath(filesDir)
                val availableBytes: Long =
                    storageManager.getAllocatableBytes(appSpecificInternalDirUuid)

                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = applicationContext.filesDir.toString()
                        )
                    }
                }
            }
        }
    }
}