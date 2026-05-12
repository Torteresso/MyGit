package com.example.gitPuzzles.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.gitPuzzles.ui.FileColor

@Composable
fun FileColor.toColorFamily(): ColorFamily {
    val ext = MaterialTheme.extendedColorScheme
    return when (this) {
        FileColor.BLUE -> ext.fileBlue
        FileColor.RED -> ext.fileRed
        FileColor.PURPLE -> ext.filePurple
        FileColor.BROWN -> ext.fileBrown
        FileColor.PINK -> ext.filePink
        FileColor.OLIVE -> ext.fileOlive
    }
}