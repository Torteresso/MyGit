package com.example.gitPuzzles.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gitPuzzles.R
import gitLogic.GitCommand
import kotlinx.coroutines.flow.collectLatest

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val workingDirectory = remember { context.applicationContext.filesDir.resolve("mainGitFolder").toPath() }
    val viewModel: HomeViewModel =
        viewModel(factory = HomeViewModel.provideFactory(workingDirectory))

    val homeUiState by viewModel.homeUiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val openCommandChooser = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.homeUiEvent.collectLatest { event ->
            when (event) {
                is HomeUiEvent.ShowSnackBar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(20.dp)
                .navigationBarsPadding()
        )
        {
            HomeScreenTopBar(
                activeBranch = homeUiState.activeBranch,
                onDeleteButtonClick = {
                    viewModel.deleteGitRepository()
                },
                modifier = Modifier.fillMaxWidth()
            )
            FileSystemGrid(
                filesUiStates = homeUiState.filesUiState,
                areFileClickable = homeUiState.areFilesClickable,
                onFileClick = { n  -> viewModel.selectFile(n)},
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            SnackbarHost(hostState = snackbarHostState)
            if (openCommandChooser.value) {
                CommandChooser(
                    onDismissRequest = { openCommandChooser.value = false },
                    onCommandButtonClick = { command -> viewModel.changeCurrentCommand(command) })
            }
            HomeScreenBottomBar(
                onCommandButtonClick = { openCommandChooser.value = true },
                onExecuteButtonClick = { viewModel.executeCurrentCommand() },
                currentCommand = homeUiState.currentCommand
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenBottomBar(
    onCommandButtonClick: () -> Unit,
    onExecuteButtonClick: () -> Unit,
    currentCommand: GitCommand,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(onClick = { onCommandButtonClick() }) {
                    Text(text = "git ${currentCommand.name}")
                }
                IconButton(onClick = { onExecuteButtonClick() }) {
                    Icon(
                        painterResource(R.drawable.start_icon_24px),
                        contentDescription = "Execute the command"
                    )
                }


            }
        }
    }
}

@Composable
fun CommandChooser(
    onDismissRequest: () -> Unit,
    onCommandButtonClick: (GitCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
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
fun CommandChooserPreview() {
    CommandChooser({}, {})
}

@Preview
@Composable
fun HomeScreenBottomBarPreview() {
    HomeScreenBottomBar({}, {}, GitCommand.Init)
}

@Preview
@Composable
fun HomeScreenTopBarPreview() {
    HomeScreenTopBar("testBranch", {})
}
