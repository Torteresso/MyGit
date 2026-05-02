package com.example.gitPuzzles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitPuzzles.R
import gitLogic.GitCommand
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val workingDirectory =
        remember { context.applicationContext.filesDir.resolve("mainGitFolder").toPath() }
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.provideFactory(workingDirectory))

    val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val openCommandChooser = rememberSaveable { mutableStateOf(false) }

    val onCommandChooserDismissRequest = remember { { openCommandChooser.value = false } }
    val onMoreCommandsClick = remember { { openCommandChooser.value = true } }

    LaunchedEffect(Unit) {
        viewModel.homeUiEvent.collectLatest { event ->
            when (event) {
                is HomeUiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 20.dp, bottom = 10.dp)
                .navigationBarsPadding()
        )
        {
            HomeScreenTopBar(
                activeBranch = homeUiState.activeBranch,
                onDeleteButtonClick = viewModel::deleteGitRepository,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.1f)
            )
            FileSystemGrid(
                filesUiStates = homeUiState.filesUiState,
                onFileClick = viewModel::onFileInteraction,
                onBlockModificationButtonClick = viewModel::modifyFileBlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp)
            )

            if (openCommandChooser.value) {
                GridOfAllCommands(
                    onDismissRequest = onCommandChooserDismissRequest,
                    onCommandButtonClick = viewModel::changeCurrentCommand
                )
            }
            HomeScreenBottomBar(
                onCommandButtonClick = viewModel::changeCurrentCommand,
                onExecuteButtonClick = viewModel::executeCurrentCommand,
                onMoreCommandsClick = onMoreCommandsClick,
                currentCommand = homeUiState.currentCommand ?: GitCommand.Init,
                modifier = Modifier.weight(0.2f)
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-100).dp)
        )
    }
}

@Composable
fun HomeScreenTopBar(
    activeBranch: String?,
    onDeleteButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        )
        {
            GitStatusSurface(activeBranch)
            IconButton(
                onClick = onDeleteButtonClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Default.Delete, contentDescription = "Remove git repository"
                )
            }
        }
    }
}

@Composable
fun HomeScreenBottomBar(
    onCommandButtonClick: (GitCommand) -> Unit,
    onExecuteButtonClick: () -> Unit,
    onMoreCommandsClick: () -> Unit,
    currentCommand: GitCommand,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "git",
                autoSize = TextAutoSize.StepBased(),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(0.5f)
            )
            CommandChooser(
                commands = listOf(GitCommand.Init),
                onCommandClick = onCommandButtonClick,
                onMoreCommandsClick = onMoreCommandsClick,
                modifier = Modifier.weight(2f)
            )

            IconButton(
                onClick = onExecuteButtonClick,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    painterResource(R.drawable.start_icon_24px),
                    contentDescription = "Execute the command"
                )
            }
        }
    }
}

@OptIn(ExperimentalGridApi::class)
@Composable
fun CommandChooser(
    commands: List<GitCommand>, onCommandClick: (GitCommand) -> Unit,
    onMoreCommandsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nbOfRows = 2
    val nbOfCols = 2
    Grid(
        config =
            {
                repeat(nbOfCols) {
                    column(1f / nbOfCols)
                }
                repeat(nbOfRows) {
                    row(1f / nbOfRows)
                }
                gap(2.dp)

            }, modifier = modifier
    )
    {
        repeat(min(nbOfCols * nbOfRows - 1, commands.size))
        { commandNumber ->
            CommandCard(commands[commandNumber], onCommandClick = onCommandClick)
        }
        TextButton(
            onClick = { onMoreCommandsClick() },
            border = BorderStroke(2.dp, color = Color.Gray),
            modifier = modifier.fillMaxSize()
        )
        {
            Text(
                text = "...",
                textAlign = TextAlign.Center,
                autoSize = TextAutoSize.StepBased(minFontSize = 1.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun CommandCard(
    command: GitCommand,
    onCommandClick: (GitCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = { onCommandClick(command) },
        border = BorderStroke(2.dp, color = Color.Gray),
        modifier = modifier.fillMaxSize()
    )
    {
        Text(
            text = command.name,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 1.sp),
            maxLines = 1
        )
    }
}

@Composable
fun GridOfAllCommands(
    onDismissRequest: () -> Unit,
    onCommandButtonClick: (GitCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize()
            )
            {
                items(GitCommand.ALL_COMMANDS) { command ->
                    TextButton(
                        onClick = {
                            onCommandButtonClick(command)
                            onDismissRequest()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = command.name)
                    }
                }
            }
        }
    }
}

@Composable
fun GitStatusSurface(activeBranch: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            if (activeBranch != null) {
                Icon(
                    painterResource(R.drawable.git_branch),
                    contentDescription = "Git active branch",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(activeBranch)
            } else Text("No git repository")
        }
    }
}

@Preview
@Composable
fun GitStatusSurfacePreview() {
    GitStatusSurface("testBranch")
}

@Preview
@Composable
fun GridOfAllCommandsPreview() {
    GridOfAllCommands({}, {})
}

@Preview
@Composable
fun HomeScreenBottomBarPreview() {
    HomeScreenBottomBar(
        {}, {}, onMoreCommandsClick = {},
        GitCommand.Init, modifier = Modifier.height(200.dp)
    )
}

@Preview
@Composable
fun HomeScreenTopBarPreview() {
    HomeScreenTopBar("testBranch", {})
}

@Preview
@Composable
fun CommandChooserPreview() {
    CommandChooser(
        listOf(GitCommand.Init),
        onCommandClick = {}, onMoreCommandsClick = {})
}