package com.example.gitPuzzles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import gitLogic.init

@Composable
fun HomeScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        )
        {
            val context = LocalContext.current
            val gitDir = remember { context.applicationContext.filesDir.toString() }
            Button(onClick = {
                init(gitDir)
            })
            {
                Text("init")
            }
        }
    }
}