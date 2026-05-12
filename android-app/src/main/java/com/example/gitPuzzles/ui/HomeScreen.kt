package com.example.gitPuzzles.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
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
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import com.example.gitPuzzles.R
import gitLogic.GitCommand
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.min

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    windowSizeClass: WindowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass,
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


    val isVertical =
        windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    Box(
        modifier = modifier
            .fillMaxSize()
            .safeContentPadding()
    ) {
        if (isVertical) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            )
            {
                HomeScreenStatusBar(
                    activeBranch = homeUiState.activeBranch,
                    onDeleteButtonClick = viewModel::deleteGitRepository,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                HorizontalDivider(
                    thickness = 0.5.dp
                )
                FileSystemGrid(
                    filesUiStates = homeUiState.filesUiState,
                    isVertical = true,
                    snackbarHostState = snackbarHostState,
                    onFileClick = viewModel::onFileInteraction,
                    onBlockModificationButtonClick = viewModel::modifyFileBlock,
                    modifier = Modifier
                        .weight(9f)
                        .fillMaxWidth()
                )

                if (openCommandChooser.value) {
                    GridOfAllCommands(
                        commandsUiState = homeUiState.commandsUiState,
                        onDismissRequest = onCommandChooserDismissRequest,
                        onCommandButtonClick = viewModel::onCommandSelection
                    )
                }
                HorizontalDivider(
                    thickness = 0.5.dp
                )
                HomeScreenCommandsBar(
                    commandsUiState = homeUiState.commandsUiState,
                    isVertical = false,
                    onCommandButtonClick = viewModel::onCommandSelection,
                    onExecuteButtonClick = viewModel::executeCurrentCommand,
                    onMoreCommandsClick = onMoreCommandsClick,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxWidth()
                )
            }

        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            )
            {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(3f)
                ) {
                    HomeScreenStatusBar(
                        activeBranch = homeUiState.activeBranch,
                        onDeleteButtonClick = viewModel::deleteGitRepository,
                        modifier = Modifier
                            .weight(2f)
                            .fillMaxWidth()
                    )
                    HomeScreenCommandsBar(
                        commandsUiState = homeUiState.commandsUiState,
                        isVertical = true,
                        onCommandButtonClick = viewModel::onCommandSelection,
                        onExecuteButtonClick = viewModel::executeCurrentCommand,
                        onMoreCommandsClick = onMoreCommandsClick,
                        modifier = Modifier
                            .weight(8f)
                            .fillMaxWidth()
                    )
                }
                VerticalDivider(thickness = 0.5.dp)
                FileSystemGrid(
                    filesUiStates = homeUiState.filesUiState,
                    isVertical = false,
                    snackbarHostState = snackbarHostState,
                    onFileClick = viewModel::onFileInteraction,
                    onBlockModificationButtonClick = viewModel::modifyFileBlock,
                    modifier = Modifier
                        .weight(7f)
                        .fillMaxHeight()
                )

                if (openCommandChooser.value) {
                    GridOfAllCommands(
                        commandsUiState = homeUiState.commandsUiState,
                        onDismissRequest = onCommandChooserDismissRequest,
                        onCommandButtonClick = viewModel::onCommandSelection
                    )
                }

            }
        }
    }
}

@Composable
fun HomeScreenStatusBar(
    activeBranch: String?,
    onDeleteButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
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
fun HomeScreenCommandsBar(
    commandsUiState: List<CommandUiState>,
    isVertical: Boolean,
    onCommandButtonClick: (GitCommand) -> Unit,
    onExecuteButtonClick: () -> Unit,
    onMoreCommandsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        if (isVertical) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround
            )
            {
                CommandBarContent(
                    commandsUiState = commandsUiState,
                    onCommandButtonClick = onCommandButtonClick,
                    onExecuteButtonClick = onExecuteButtonClick,
                    onMoreCommandsClick = onMoreCommandsClick,
                    textModifier = Modifier
                        .weight(1f),
                    commandChooserModifier = Modifier
                        .weight(2f)
                        .padding(4.dp),
                    executeButtonModifier = Modifier.weight(1f)
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommandBarContent(
                    commandsUiState = commandsUiState,
                    onCommandButtonClick = onCommandButtonClick,
                    onExecuteButtonClick = onExecuteButtonClick,
                    onMoreCommandsClick = onMoreCommandsClick,
                    textModifier = Modifier
                        .weight(1f),
                    commandChooserModifier = Modifier
                        .weight(2f)
                        .padding(4.dp),
                    executeButtonModifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun CommandBarContent(
    commandsUiState: List<CommandUiState>,
    onCommandButtonClick: (GitCommand) -> Unit,
    onExecuteButtonClick: () -> Unit,
    onMoreCommandsClick: () -> Unit,
    @SuppressLint("ModifierParameter") textModifier: Modifier = Modifier,
    commandChooserModifier: Modifier = Modifier,
    executeButtonModifier: Modifier = Modifier,
) {
    Text(
        text = "git",
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        modifier = textModifier
    )
    CommandChooser(
        commandsUiState = commandsUiState,
        onCommandClick = onCommandButtonClick,
        onMoreCommandsClick = onMoreCommandsClick,
        modifier = commandChooserModifier
    )

    IconButton(
        onClick = onExecuteButtonClick,
        modifier = executeButtonModifier
    ) {
        Icon(
            painterResource(R.drawable.start_icon_24px),
            contentDescription = "Execute the command"
        )
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
            CommandCard(
                commandsUiState[commandNumber],
                onCommandClick = onCommandClick,
                modifier = Modifier.fillMaxSize()
            )
        }
        TextButton(
            colors = ButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = Color.Unspecified,
                disabledContentColor = Color.Unspecified
            ),
            onClick = { onMoreCommandsClick() },
            shape = RoundedCornerShape(2.dp),
            border = BorderStroke(0.5.dp, color = MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxSize()
        )
        {
            Text(
                text = "...",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
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
        colors = ButtonColors(
            containerColor = if (commandUiState.isSelected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (commandUiState.isSelected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = Color.Unspecified,
            disabledContentColor = Color.Unspecified
        ),
        onClick = { onCommandClick(commandUiState.command) },
        shape = RoundedCornerShape(2.dp),
        border = BorderStroke(0.5.dp, color = MaterialTheme.colorScheme.outline),
        modifier = modifier
    )
    {
        Text(
            text = commandUiState.command.name,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
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
            modifier = modifier,
            shape = RoundedCornerShape(4.dp),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(6.dp),
            )
            {
                items(commandsUiState) { commandUiState ->
                    TextButton(
                        colors = ButtonColors(
                            containerColor = if (commandUiState.isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (commandUiState.isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = Color.Unspecified,
                            disabledContentColor = Color.Unspecified
                        ),
                        onClick = {
                            onCommandButtonClick(commandUiState.command)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(2.dp),
                        border = BorderStroke(0.5.dp, color = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.padding(4.dp)
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
            Text(text = activeBranch, style = MaterialTheme.typography.titleMedium)
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
            CommandUiState(command = GitCommand.Init, isSelected = false),

            CommandUiState(command = GitCommand.Add, isSelected = true),
            CommandUiState(command = GitCommand.Status, isSelected = false)
        ),
        onDismissRequest = {},
        onCommandButtonClick = {},
        )

}

@Preview
@Composable
fun HomeScreenVerticalCommandsBarPreview() {
    HomeScreenCommandsBar(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, isSelected = false),

            CommandUiState(command = GitCommand.Add, isSelected = true),
            CommandUiState(command = GitCommand.Status, isSelected = true)
        ),
        onCommandButtonClick = { _ -> },
        onExecuteButtonClick = {},
        onMoreCommandsClick = {},
        isVertical = true,
        modifier = Modifier.height(200.dp)
    )
}


@Preview
@Composable
fun HomeScreenHorizontalCommandsBarPreview() {
    HomeScreenCommandsBar(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, isSelected = false),

            CommandUiState(command = GitCommand.Add, isSelected = true),
            CommandUiState(command = GitCommand.Status, isSelected = false)
        ),
        onCommandButtonClick = { _ -> },
        onExecuteButtonClick = {},
        onMoreCommandsClick = {},
        isVertical = false,
        modifier = Modifier.height(200.dp)
    )
}

@Preview
@Composable
fun HomeScreenStatusBarPreview() {
    HomeScreenStatusBar(
        activeBranch = "testBranch",
        onDeleteButtonClick = {},
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
fun CommandChooserPreview() {
    CommandChooser(
        commandsUiState = listOf(
            CommandUiState(command = GitCommand.Init, isSelected = false),

            CommandUiState(command = GitCommand.Add, isSelected = true),
            CommandUiState(command = GitCommand.Status, isSelected = false)
        ),
        onCommandClick = {}, onMoreCommandsClick = {})
}