package com.example.gitPuzzles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
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
import com.example.gitPuzzles.themlng.Brown
import com.example.gitPuzzles.themlng.Green
import com.example.gitPuzzles.themlng.RedOrange
import com.example.gitPuzzles.themlng.White
import gitLogic.FileStatus

@Composable
fun FileSystemGrid(filesUiStates: List<FileUiState>, modifier: Modifier = Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
    )
    {
        items(count = filesUiStates.size) { fileNumber ->
            FileCard(
                fileNumber = fileNumber,
                fileUiState = filesUiStates[fileNumber],
                modifier = Modifier
                    .aspectRatio(0.8f)
                    .padding(4.dp)
            )
        }
    }
}


@Composable
fun FileCard(fileNumber: Int, fileUiState: FileUiState, modifier: Modifier = Modifier) {

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = White,
        border = BorderStroke(2.dp, Color.Gray),
        modifier = modifier
    ) {

        Box(modifier = Modifier.fillMaxSize()) {

            Surface(
                shape = CircleShape,
                color = fileUiState.color,
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .align(Alignment.BottomStart)
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

            FileStatusBadges(
                fileStatus = fileUiState.status,
                modifier = Modifier.fillMaxWidth()
                    .padding(4.dp)
            )

        }
    }
}

@Composable
fun FileStatusBadges(fileStatus: List<FileStatusUi>, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier,
        horizontalArrangement = Arrangement.End)
    {
        fileStatus.forEach { status ->
            Badge(containerColor = status.color, modifier = Modifier.padding(3.dp).fillMaxHeight(0.18f).aspectRatio(1f)) {
                Icon(
                    imageVector =  status.icon,
                    contentDescription = status.label,
                    tint = Color.White,
                    modifier = Modifier.fillMaxSize(0.7f)

                )
            }
        }
    }
}

@Preview
@Composable
fun FileStatusBadgesPreview() {
    FileStatusBadges(fileStatus =
        listOf(
          FileStatus.ADDED.toUi(),
            FileStatus.DELETED_STAGED.toUi(),
            FileStatus.DELETED_UNSTAGED.toUi(),
            FileStatus.MODIFIED_UNSTAGED.toUi(),
            FileStatus.MODIFIED_STAGED.toUi(),
            FileStatus.UNMODIFIED.toUi(),
            FileStatus.UNTRACKED.toUi(),
            FileStatus.CONFLICT.toUi(),

        ), modifier =Modifier.fillMaxSize())
}

@Preview
@Composable
fun FileSystemGridPreview() {
    FileSystemGrid(
        filesUiStates = listOf(
            FileUiState(
                color = Blue,
                status = listOf(
                    FileStatusUi(
                        color = RedOrange,
                        icon = Icons.Default.Add,
                        label = "Added"
                    )
                )
            ),
            FileUiState(
                color = Brown, status = listOf(
                    FileStatusUi(color = RedOrange, icon = Icons.Default.Add, label = "Added"),
                    FileStatusUi(color = RedOrange, icon = Icons.Default.Edit, label = "Staged")
                )
            ),
            FileUiState(
                color = Green,
                status = listOf(
                    FileStatusUi(
                        color = RedOrange,
                        icon = Icons.Default.Edit,
                        label = "Staged"
                    )
                )
            )
        ),


        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
fun FileCardPreview() {
    FileCard(
        fileNumber = 2,
        fileUiState =
            FileUiState(
                color = Blue, status = listOf(
                    FileStatusUi(color = RedOrange, icon = Icons.Default.Add, label = "Added"),
                    FileStatusUi(color = RedOrange, icon = Icons.Default.Edit, label = "Staged")
                )
            ),
    )
}


