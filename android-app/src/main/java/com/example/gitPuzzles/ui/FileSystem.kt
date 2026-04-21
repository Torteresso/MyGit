package com.example.gitPuzzles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gitPuzzles.themlng.Blue
import com.example.gitPuzzles.themlng.Brown
import com.example.gitPuzzles.themlng.Green
import com.example.gitPuzzles.themlng.StatusBackgroundColor
import com.example.gitPuzzles.themlng.White
import gitLogic.FileStatus

@Composable
fun FileSystemGrid(
    filesUiStates: List<FileUiState>,
    areFileClickable: Boolean,
    onFileClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
    )
    {
        items(count = filesUiStates.size) { fileNumber ->
            FileCard(
                fileNumber = fileNumber,
                fileUiState = filesUiStates[fileNumber],
                isFileClickable = areFileClickable,
                onFileClick = onFileClick,
                modifier = Modifier
                    .aspectRatio(0.8f)
                    .padding(4.dp)
            )
        }
    }
}


@Composable
fun FileCard(
    fileNumber: Int,
    fileUiState: FileUiState,
    isFileClickable: Boolean,
    onFileClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {

    var surfaceOffset by remember { mutableStateOf(Offset.Zero) }
    var circleSize by remember { mutableStateOf(IntSize.Zero) }
    val fileBorderColor = if (fileUiState.isSelected) Green else Color.Gray

    Box(modifier = modifier.fillMaxSize())
    {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = White,
            border = BorderStroke(3.dp, fileBorderColor),
            modifier = Modifier
                .fillMaxSize(0.9f)
                .align(Alignment.Center)
                .onGloballyPositioned { coords ->
                    surfaceOffset = coords.positionInParent()
                }
                .clickable(enabled = isFileClickable, onClick = { onFileClick(fileNumber) })
        ) {
            FileStatusBadges(
                fileStatus = fileUiState.status,
                modifier = Modifier
                    .align(Alignment.TopEnd)
            )
        }
        Surface(
            shape = CircleShape,
            color = fileUiState.color,
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .aspectRatio(1f)
                .align(Alignment.TopStart)
                .onGloballyPositioned { coords ->
                    circleSize = coords.size
                }
                .offset {
                    IntOffset(
                        x = surfaceOffset.x.toInt() - circleSize.width / 4,
                        y = surfaceOffset.y.toInt() - circleSize.height / 4
                    )
                }
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
@Composable
fun FileStatusBadges(fileStatus: List<FileStatusUi>, modifier: Modifier = Modifier) {


    Column(
        modifier = modifier.padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        fileStatus.forEach { status ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StatusBackgroundColor,
                modifier = Modifier.fillMaxWidth(0.25f).aspectRatio(1.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                     ,
                    horizontalArrangement = Arrangement.spacedBy(space = 2.dp, alignment = Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.statusCodeX.first.toString(),
                        color = status.statusCodeX.second,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 2.sp,
                            maxFontSize = 40.sp,
                        ),modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = status.statusCodeY.first.toString(),
                        color = status.statusCodeY.second,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 2.sp,
                            maxFontSize = 40.sp,
                        ),modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun FileStatusBadgesPreview() {
    FileStatusBadges(
        fileStatus =
            listOf(
                FileStatus.ADDED.toUi(),
                FileStatus.DELETED_STAGED.toUi(),
                FileStatus.DELETED_UNSTAGED.toUi(),
                FileStatus.MODIFIED_UNSTAGED.toUi(),
                FileStatus.MODIFIED_STAGED_DELETED.toUi(),
                FileStatus.UNMODIFIED.toUi(),
                FileStatus.UNTRACKED.toUi(),
            ), modifier = Modifier.fillMaxSize()
    )
}

@Preview
@Composable
fun FileSystemGridPreview() {
    FileSystemGrid(
        filesUiStates = listOf(
            FileUiState(
                color = Blue,
                status = listOf(

                    FileStatus.ADDED.toUi(),
                )
            ),
            FileUiState(
                color = Brown, status = listOf(

                    FileStatus.ADDED.toUi(),
                    FileStatus.MODIFIED_STAGED_DELETED.toUi(),
                )
            ),
            FileUiState(
                color = Green,
                status = listOf(

                    FileStatus.DELETED_STAGED.toUi(),
                )
            )
        ),
        areFileClickable = true,
        onFileClick = {},
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

                    FileStatus.MODIFIED_UNSTAGED.toUi(),
                    FileStatus.ADDED.toUi(),
                )
            ),
        isFileClickable = true,
        onFileClick = {}
    )
}




