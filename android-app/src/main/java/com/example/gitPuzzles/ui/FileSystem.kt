package com.example.gitPuzzles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.gitPuzzles.themlng.Blue
import com.example.gitPuzzles.themlng.Green
import com.example.gitPuzzles.themlng.Orange
import com.example.gitPuzzles.themlng.White

@Composable
fun FileSystemGrid(filesColor: List<Color>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
    )
    {
        items(count = filesColor.size) { fileNumber ->
            FileCard(
                fileNumber = fileNumber, fileColor = filesColor[fileNumber], modifier = Modifier
                    .aspectRatio(0.8f)
                    .padding(4.dp)
            )
        }
    }
}


@Composable
fun FileCard(fileNumber: Int, fileColor: Color, modifier: Modifier = Modifier) {

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = White,
        border = BorderStroke(2.dp, Color.Gray),
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {

            Surface(
                shape = CircleShape,
                color = fileColor,
                modifier = Modifier
                    .fillMaxSize(0.4f)
                    .aspectRatio(1f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = fileNumber.toString(),
                        color = Color.White,
                        autoSize = TextAutoSize.StepBased(),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(0.5f)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun FileSystemGridPreview() {
    FileSystemGrid(
        filesColor = listOf(Blue, Orange, Green),
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
fun FileCardPreview() {
    FileCard(2, fileColor = Green, modifier = Modifier.fillMaxSize())
}


