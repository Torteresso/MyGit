package com.example.gitPuzzles.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.gitPuzzles.numberToLetter
import com.example.gitPuzzles.themlng.DarkGreen
import com.example.gitPuzzles.themlng.StatusBackgroundColor
import com.example.gitPuzzles.themlng.Transparent
import com.example.gitPuzzles.themlng.White
import com.example.gitPuzzles.ui.theme.extendedColorScheme
import com.example.gitPuzzles.ui.theme.toColorFamily
import gitLogic.FileStatus

@Composable
fun FileSystemGrid(
    filesUiStates: List<FileUiState>,
    onFileClick: (Int) -> Unit,
    onBlockModificationButtonClick: (Int, Int, BlockModificationFlag) -> Unit,
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
                onFileClick = onFileClick,
                onBlockModificationButtonClick = onBlockModificationButtonClick,
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
    onFileClick: (Int) -> Unit,
    onBlockModificationButtonClick: (Int, Int, BlockModificationFlag) -> (Unit),
    modifier: Modifier = Modifier,
) {

    var cardInteriorSurfaceOffset by remember { mutableStateOf(Offset.Zero) }
    var cardInteriorSurfaceSize by remember { mutableStateOf(IntSize(1, 1)) }
    var nameCircleSize by remember { mutableStateOf(IntSize(1, 1)) }
    var blockButtonsCircleSize by remember { mutableStateOf(IntSize(1, 1)) }

    val fileBlocksPositionInFractionHeight = 0.3f
    val fileBlocksBottomPaddingInFractionHeight = 0.05f

    Box(modifier = modifier.fillMaxSize())
    {
        FileCardInterior(
            fileUiState = fileUiState,
            gradientSize = nameCircleSize.width.toFloat() * 2,
            onFileClick = onFileClick,
            fileNumber = fileNumber,
            fileStatusBadgeModifier = Modifier
                .align(Alignment.TopEnd),
            fileBlocksPositionInFractionHeight = fileBlocksPositionInFractionHeight,
            fileBlocksBottomPaddingInFractionHeight = fileBlocksBottomPaddingInFractionHeight,
            modifier = Modifier
                .fillMaxSize(0.9f)
                .align(Alignment.Center)
                .onGloballyPositioned { coords ->
                    cardInteriorSurfaceOffset = coords.positionInParent()
                    cardInteriorSurfaceSize = coords.size
                }

        )

        if (fileUiState.interactionState == FileInteractionState.FOCUSED) {
            // For measurement only
            val buttonSizeWidthFraction = 0.15f
            Box(
                modifier = Modifier
                    .fillMaxWidth(buttonSizeWidthFraction)
                    .aspectRatio(1f)
                    .onGloballyPositioned { coords ->
                        blockButtonsCircleSize = coords.size
                    }
                    .alpha(0f)
            )
            FileBlockAddAndRemoveButtons(
                color = fileUiState.color,
                cardInteriorSurfaceSize = cardInteriorSurfaceSize,
                cardInteriorSurfaceOffset = cardInteriorSurfaceOffset,
                blockButtonsCircleSize = blockButtonsCircleSize,
                heightPositionFraction = fileBlocksPositionInFractionHeight,
                fileNumber = fileNumber,
                blockNumber = 1,
                onButtonClick = onBlockModificationButtonClick
            )
            FileBlockAddAndRemoveButtons(
                color = fileUiState.color,
                cardInteriorSurfaceSize = cardInteriorSurfaceSize,
                cardInteriorSurfaceOffset = cardInteriorSurfaceOffset,
                blockButtonsCircleSize = blockButtonsCircleSize,
                heightPositionFraction =
                    (fileBlocksPositionInFractionHeight +
                            (1 - fileBlocksBottomPaddingInFractionHeight -
                                    fileBlocksPositionInFractionHeight) / 2),
                fileNumber = fileNumber,
                blockNumber = 2,
                onButtonClick = onBlockModificationButtonClick
            )

        }

        FileCardNameIndicator(
            fileUiState = fileUiState,
            fileNumber = fileNumber,
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .aspectRatio(1f)
                .onGloballyPositioned { coords ->
                    nameCircleSize = coords.size
                }
                .offset {
                    IntOffset(
                        x = cardInteriorSurfaceOffset.x.toInt() - nameCircleSize.width / 4,
                        y = cardInteriorSurfaceOffset.y.toInt() - nameCircleSize.height / 4
                    )
                }
        )
    }
}

@Composable
fun FileBlockAddAndRemoveButtons(
    color: FileColor, cardInteriorSurfaceOffset: Offset,
    blockButtonsCircleSize: IntSize,
    cardInteriorSurfaceSize: IntSize,
    heightPositionFraction: Float,
    fileNumber: Int,
    blockNumber: Int,
    onButtonClick: (Int, Int, BlockModificationFlag) -> Unit
) {
    val buttonSizeInWidthFraction = 0.175f
    FileBlockModificationButton(
        color = color,
        icon = Icons.Default.Remove,
        iconDescription = "Remove last block",
        fileNumber = fileNumber,
        blockNumber = blockNumber,
        modificationFlag = BlockModificationFlag.REMOVE_LINE,
        onButtonClick = onButtonClick,
        modifier =
            Modifier
                .fillMaxWidth(buttonSizeInWidthFraction)
                .aspectRatio(1f)
                .offset {
                    IntOffset(
                        x = cardInteriorSurfaceOffset.x.toInt() - blockButtonsCircleSize.width / 2,
                        y = cardInteriorSurfaceOffset.y.toInt()
                                + (cardInteriorSurfaceSize.height * heightPositionFraction).toInt()
                    )
                }
    )
    FileBlockModificationButton(
        color = color,
        icon = Icons.Default.Add,
        iconDescription = "Add new block",
        onButtonClick = onButtonClick,
        fileNumber = fileNumber,
        blockNumber = blockNumber,
        modificationFlag = BlockModificationFlag.ADD_LINE,
        modifier =
            Modifier
                .fillMaxWidth(buttonSizeInWidthFraction)
                .aspectRatio(1f)
                .offset {
                    IntOffset(
                        x = cardInteriorSurfaceOffset.x.toInt() / 2 + cardInteriorSurfaceSize.width
                                - blockButtonsCircleSize.width / 2,
                        y = cardInteriorSurfaceOffset.y.toInt()
                                + (cardInteriorSurfaceSize.height * heightPositionFraction).toInt()
                    )
                }
    )
}

@Composable
fun FileBlockModificationButton(
    color: FileColor,
    icon: ImageVector,
    iconDescription: String,
    fileNumber: Int,
    blockNumber: Int,
    modificationFlag: BlockModificationFlag,
    onButtonClick: (Int, Int, BlockModificationFlag) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = color.toColorFamily().colorContainer,
        modifier = modifier
            .clickable(onClick = { onButtonClick(fileNumber, blockNumber, modificationFlag) })

    ) {
        Icon(
            imageVector = icon,
            contentDescription = iconDescription,
            tint = color.toColorFamily().onColorContainer
        )

    }
}

@Composable
fun FileCardNameIndicator(
    fileUiState: FileUiState,
    fileNumber: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = fileUiState.color.toColorFamily().colorContainer,
        contentColor = White,
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = numberToLetter(fileNumber),
                autoSize = TextAutoSize.StepBased(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

@Composable
fun FileCardInterior(
    fileUiState: FileUiState,
    gradientSize: Float,
    onFileClick: (Int) -> Unit,
    fileNumber: Int,
    fileBlocksPositionInFractionHeight: Float,
    fileBlocksBottomPaddingInFractionHeight: Float,
    @SuppressLint("ModifierParameter") fileStatusBadgeModifier: Modifier,
    modifier: Modifier = Modifier,
) {

    val unfocusedCardBorderColor = MaterialTheme.colorScheme.outline
    val focusedCardBorderColor = fileUiState.color.toColorFamily().colorContainer
    val selectedCardBorderColor = MaterialTheme.extendedColorScheme.fileGreen.colorContainer
    val fileBorderColor = remember(
        fileUiState.interactionState,
        focusedCardBorderColor,
        unfocusedCardBorderColor,
        selectedCardBorderColor
    ) {
        when (fileUiState.interactionState) {
            FileInteractionState.SELECTED -> {

                Brush.linearGradient(
                    listOf(
                        selectedCardBorderColor,
                        DarkGreen
                    )
                )
            }

            FileInteractionState.FOCUSED -> SolidColor(focusedCardBorderColor)

            FileInteractionState.IDLE -> SolidColor(unfocusedCardBorderColor)
        }
    }

    val unfocusedCardColor = MaterialTheme.colorScheme.surfaceContainer
    val focusedCardColor = fileUiState.color.toColorFamily().colorContainer
    val fileBackgroundBrush = remember(
        fileUiState.interactionState,
        focusedCardColor,
        gradientSize,
        unfocusedCardColor
    ) {
        if (fileUiState.interactionState == FileInteractionState.FOCUSED) {
            Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    focusedCardColor.copy(alpha = 0.15f),
                    focusedCardColor.copy(alpha = 0.3f),
                ),
                center = Offset.Unspecified,
                radius = gradientSize
            )
        } else {
            SolidColor(unfocusedCardColor)
        }
    }
    val cardShape = RoundedCornerShape(8.dp)
    Surface(
        shape = cardShape,
        color = Transparent,
        border = BorderStroke(3.dp, fileBorderColor),
        modifier = modifier
            .background(brush = fileBackgroundBrush, shape = cardShape)
            .clickable(onClick = { onFileClick(fileNumber) })
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp)
        ) {
            Spacer(modifier = Modifier.weight(fileBlocksPositionInFractionHeight))
            FileBlocks(
                block1 = fileUiState.block1,
                block2 = fileUiState.block2,
                fileColor = fileUiState.color,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1 - fileBlocksPositionInFractionHeight - fileBlocksBottomPaddingInFractionHeight)
            )
            Spacer(modifier = Modifier.weight(fileBlocksBottomPaddingInFractionHeight))
        }
        FileStatusBadges(
            fileStatus = fileUiState.status,
            modifier = fileStatusBadgeModifier
        )
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
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .aspectRatio(1.3f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 2.dp,
                        alignment = Alignment.CenterHorizontally
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = status.statusCodeX.first.toString(),
                        color = status.statusCodeX.second,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp

                        , modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = status.statusCodeY.first.toString(),
                        color = status.statusCodeY.second,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp
                       , modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FileBlocks(
    block1: List<Float>,
    block2: List<Float>,
    fileColor: FileColor,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SingleFileBlock(
            block = block1, fileColor = fileColor, modifier = Modifier
                .weight(1f)
                .padding(6.dp)
        )
        SingleFileBlock(
            block = block2, fileColor = fileColor, modifier = Modifier
                .weight(1f)
                .padding(6.dp)
        )
    }
}

@Composable
fun SingleFileBlock(block: List<Float>, fileColor: FileColor, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val maxLineHeight = 8.dp
        Column(modifier = Modifier.fillMaxSize()) {
            block.forEach { lineLength ->
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 0.dp,
                        topEnd = 0.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 4.dp
                    ),
                    tonalElevation = 2.dp,
                    border = BorderStroke(
                        0.2.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    ),
                    color = fileColor.toColorFamily().colorContainer, modifier = Modifier
                        .fillMaxWidth(lineLength)
                        .then(
                            if (this@BoxWithConstraints.maxHeight / block.size > maxLineHeight) Modifier.height(
                                maxLineHeight
                            )
                            else Modifier.weight(1f)

                        )
                ) {
                }
            }
        }
    }
}

@Preview
@Composable
fun FileSystemGridPreview() {
    FileSystemGrid(
        filesUiStates = listOf(
            FileUiState(
                color = FileColor.BLUE,
                status = listOf(

                    FileStatus.ADDED.toUi(),
                ), interactionState = FileInteractionState.FOCUSED
            ),
            FileUiState(
                color = FileColor.BROWN, status = listOf(

                    FileStatus.ADDED.toUi(),
                    FileStatus.MODIFIED_STAGED_DELETED.toUi(),
                ), interactionState = FileInteractionState.SELECTED

            ),
            FileUiState(
                color = FileColor.BROWN,
                status = listOf(

                    FileStatus.DELETED_STAGED.toUi(),
                )
            ),
        ),
        onFileClick = {},
        onBlockModificationButtonClick = { _, _, _ -> },
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
                color = FileColor.BLUE, status = listOf(

                    FileStatus.MODIFIED_UNSTAGED.toUi(),
                    FileStatus.ADDED.toUi(),
                ), block1 = listOf(1.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f),
                block2 = listOf(0.8f, 0.2f, 0.1f)
            ),
        onFileClick = {},
        onBlockModificationButtonClick = { _, _, _ -> }
    )
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
fun FileBlocksPreview() {
    FileBlocks(
        listOf(0.7f, 0.5f, 0.8f),
        listOf(0.7f, 0.3f, 0.4f),
        fileColor = FileColor.BLUE,
        modifier = Modifier.fillMaxSize()
    )
}
