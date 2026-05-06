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
import androidx.compose.material3.MaterialTheme
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
import com.example.gitPuzzles.themlng.Green
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

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .navigationBarsPadding()
        )
        {
            HomeScreenTopBar(
                activeBranch = homeUiState.activeBranch,
                onDeleteButtonClick = viewModel::deleteGitRepository,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.12f)
            )
            FileSystemGrid(
                filesUiStates = homeUiState.filesUiState,
                onFileClick = viewModel::onFileInteraction,
                onBlockModificationButtonClick = viewModel::modifyFileBlock,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp)
            )

            if (openCommandChooser.value) {
                GridOfAllCommands(
                    commandsUiState = homeUiState.commandsUiState,
                    onDismissRequest = onCommandChooserDismissRequest,
                    onCommandButtonClick = viewModel::onCommandSelection
                )
            }
            HomeScreenBottomBar(
                commandsUiState = homeUiState.commandsUiState,
                onCommandButtonClick = viewModel::onCommandSelection,
                onExecuteButtonClick = viewModel::executeCurrentCommand,
                onMoreCommandsClick = onMoreCommandsClick,
                modifier = Modifier.weight(0.2f)
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-160).dp)
        )
    }
}

@Composable
fun HomeScreenTopBar(
    activeBranch: String?,
    onDeleteButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, end = 4.dp, start = 4.dp)
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
    commandsUiState: List<CommandUiState>,
    onCommandButtonClick: (GitCommand) -> Unit,
    onExecuteButtonClick: () -> Unit,
    onMoreCommandsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
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
                commandsUiState = commandsUiState,
                onCommandClick = onCommandButtonClick,
                onMoreCommandsClick = onMoreCommandsClick,
                modifier = Modifier
                    .weight(2f)
                    .padding(4.dp)
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
    commandsUiState: List<CommandUiState>,
    onCommandClick: (GitCommand) -> Unit,
    onMoreCommandsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Grid(
        config =
            {
                repeat(HomeViewModel.COMMAND_CHOOSER_NB_COLS) {
                    column(1f / HomeViewModel.COMMAND_CHOOSER_NB_COLS)
                }
                repeat(HomeViewModel.COMMAND_CHOOSER_NB_ROWS) {
                    row(1f / HomeViewModel.COMMAND_CHOOSER_NB_ROWS)
                }
                gap(2.dp)

            }, modifier = modifier
    )
    {
        repeat(
            min(
                HomeViewModel.COMMAND_CHOOSER_NB_COLS *
                        HomeViewModel.COMMAND_CHOOSER_NB_ROWS - 1,
                commandsUiState.size
            )
        )
        { commandNumber ->
            CommandCard(commandsUiState[commandNumber], onCommandClick = onCommandClick)
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
    commandUiState: CommandUiState,
    onCommandClick: (GitCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = { onCommandClick(commandUiState.command) },
        border = BorderStroke(2.dp, color = commandUiState.color),
        modifier = modifier.fillMaxSize()
    )
    {
        Text(
            text = commandUiState.command.name,
            textAlign = TextAlign.Center,
            autoSize = TextAutoSize.StepBased(minFontSize = 0.sp),
            maxLines = 1
        )
    }
}

@Composable
fun GridOfAllCommands(
    commandsUiState: List<CommandUiState>,
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
                items(commandsUiState) { commandUiState ->
                    TextButton(
                        onClick = {
                            onCommandButtonClick(commandUiState.command)
                            onDismissRequest()
                        },
                        border = BorderStroke(2.dp, color = commandUiState.color),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = commandUiState.command.name)
                    }
                }
            }
        }
    }
}

@Composable
fun GitStatusSurface(activeBranch: String?, modifier: Modifier = Modifier) {

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.padding(4.dp)
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

@Preview
@Composable
fun GitStatusSurfacePreview() {
    GitStatusSurface(activeBranch = "testBranch")
}

@Preview
@Composable
fun GridOfAllCommandsPreview() {
    GridOfAllCommands(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, color = Color.Gray),

            CommandUiState(command = GitCommand.Add, color = Green),
            CommandUiState(command = GitCommand.Status, color = Color.Gray)
        ),
        onDismissRequest = {},
        onCommandButtonClick = {})

}

@Preview
@Composable
fun HomeScreenBottomBarPreview() {
    HomeScreenBottomBar(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, color = Color.Gray),

            CommandUiState(command = GitCommand.Add, color = Green),
            CommandUiState(command = GitCommand.Status, color = Color.Gray)
        ),
        onCommandButtonClick = { _ -> },
        onExecuteButtonClick = {},
        onMoreCommandsClick = {},
        modifier = Modifier.height(200.dp)
    )
}

@Preview
@Composable
fun HomeScreenTopBarPreview() {
    HomeScreenTopBar(activeBranch = "testBranch", onDeleteButtonClick = {})
}

@Preview
@Composable
fun CommandChooserPreview() {
    CommandChooser(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, color = Color.Gray),

            CommandUiState(command = GitCommand.Add, color = Green),
            CommandUiState(command = GitCommand.Status, color = Color.Gray)
        ),
        onCommandClick = {}, onMoreCommandsClick = {})
}