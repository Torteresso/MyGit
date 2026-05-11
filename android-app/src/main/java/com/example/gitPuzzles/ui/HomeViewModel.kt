package com.example.gitPuzzles.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitPuzzles.themlng.Green
import com.example.gitPuzzles.themlng.Red
import com.example.gitPuzzles.themlng.RedOrange
import com.example.gitPuzzles.themlng.Transparent
import com.example.gitPuzzles.themlng.White
import com.example.gitPuzzles.ui.HomeUiEvent.ShowSnackBar
import gitLogic.AddConfig
import gitLogic.FileStatus
import gitLogic.GitCommand
import gitLogic.InitConfig
import gitLogic.JGit
import gitLogic.StatusConfig
import gitLogic.getActiveBranch
import gitLogic.repoDelete
import gitLogic.repoFind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.notExists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random


private data class HomeState(
    val activeBranch: String? = null,
    val currentCommand: GitCommand? = null,
    val filesInteractionMode: FilesInteractionMode = FilesInteractionMode.ARE_FOCUSABLE,
    val filesState: List<FileState> = listOf(),
    val commandsState: List<CommandState> = listOf()
)

data class HomeUiState(
    val activeBranch: String? = null,
    val currentCommand: GitCommand? = null,
    val filesInteractionMode: FilesInteractionMode = FilesInteractionMode.ARE_FOCUSABLE,
    val filesUiState: List<FileUiState> = listOf(),
    val commandsUiState: List<CommandUiState> = listOf()
)

private fun HomeState.toUiState(): HomeUiState {
    return HomeUiState(
        activeBranch = this.activeBranch,
        currentCommand = this.currentCommand,
        filesInteractionMode = this.filesInteractionMode,
        filesUiState = this.filesState.map { it.toUiState() },
        commandsUiState = this.commandsState.map { it.toUiState() }
    )
}

private val fileIndexToColorMap = mapOf(
    0 to FileColor.BLUE,
    1 to FileColor.RED,
    2 to FileColor.PURPLE,
    3 to FileColor.BROWN,
    4 to FileColor.PINK,
    5 to FileColor.OLIVE,
)


enum class FilesInteractionMode {
    ARE_SELECTABLE, ARE_FOCUSABLE, ARE_IDLE
}

enum class FileInteractionState {
    SELECTED, FOCUSED, IDLE
}

enum class FileColor {
    BLUE, RED, PURPLE, BROWN, PINK, OLIVE
}

private data class FileState(
    val path: Path,
    val fileIndex: Int,
    val interactionState: FileInteractionState = FileInteractionState.IDLE,
    val status: List<FileStatus> = listOf(),
    val block1: List<Float> = listOf(),
    val block2: List<Float> = listOf()
)

data class FileUiState(
    val color: FileColor = FileColor.BLUE,
    val interactionState: FileInteractionState = FileInteractionState.IDLE,
    val status: List<FileStatusUi> = listOf(),
    val block1: List<Float> = listOf(),
    val block2: List<Float> = listOf()
)

private fun FileState.toUiState(): FileUiState {
    return FileUiState(
        color = fileIndexToColorMap.getValue(this.fileIndex % fileIndexToColorMap.size),
        interactionState = this.interactionState,
        status = this.status.map { it.toUi() },
        block1 = this.block1,
        block2 = this.block2
    )
}

enum class BlockModificationFlag {
    ADD_LINE, REMOVE_LINE
}

object BlockConfig {
    const val MIN_LINE_NUMBER = 0
    const val MAX_LINE_NUMBER = 8
    const val MAX_BLOCK_VALUE = 1f
    const val MIN_BLOCK_VALUE = 0.1f
    const val MAX_DIFF_BETWEEN_LINE_VALUE = 0.5f

    val VALID_LINE_RANGE = MIN_LINE_NUMBER..MAX_LINE_NUMBER
}

data class FileStatusUi(
    val statusCodeX: Pair<Char, Color> = Pair(' ', White),
    val statusCodeY: Pair<Char, Color> = Pair(' ', White),
    val label: String = "no status yet"
)

// @formatter:off
fun FileStatus.toUi(): FileStatusUi = when (this) {
    FileStatus.ADDED -> FileStatusUi(Pair('A', Green), Pair(' ', Transparent), "Added")
    FileStatus.MODIFIED_STAGED -> FileStatusUi(Pair('M', Green), Pair(' ', Transparent), "Modified (staged)")
    FileStatus.DELETED_STAGED -> FileStatusUi(Pair('D', Green), Pair(' ', Transparent), "Deleted (staged)")
    FileStatus.MODIFIED_UNSTAGED -> FileStatusUi(Pair(' ', Transparent), Pair('M', Red), "Modified")
    FileStatus.DELETED_UNSTAGED -> FileStatusUi(Pair(' ', Transparent), Pair('D', Red), "Deleted")
    FileStatus.UNTRACKED -> FileStatusUi(Pair('?', Red), Pair('?', Red), "Untracked")
    FileStatus.IGNORED -> FileStatusUi(Pair('!', Red), Pair('!', Red), "Ignored")
    FileStatus.MODIFIED_STAGED_UNSTAGED -> FileStatusUi(Pair('M', Green), Pair('M', Red), "Modified (partially staged)")
    FileStatus.ADDED_MODIFIED -> FileStatusUi(Pair('A', Green), Pair('M', Red), "Added (modified in worktree)")
    FileStatus.ADDED_DELETED -> FileStatusUi(Pair('A', Green), Pair('D', Red), "Added (deleted in worktree)")
    FileStatus.MODIFIED_STAGED_DELETED -> FileStatusUi(Pair('M', Green), Pair('D', Red), "Modified (deleted in worktree)")
    FileStatus.DELETED_STAGED_MODIFIED -> FileStatusUi(Pair('D', Green), Pair('M', Red), "Deleted (modified in worktree)")
    FileStatus.CONFLICT_BOTH_MODIFIED -> FileStatusUi(Pair('U', RedOrange), Pair('U', RedOrange), "Conflict (both modified)")
    FileStatus.CONFLICT_BOTH_ADDED -> FileStatusUi(Pair('A', RedOrange), Pair('A', RedOrange), "Conflict (both added)")
    FileStatus.CONFLICT_BOTH_DELETED -> FileStatusUi(Pair('D', RedOrange), Pair('D', RedOrange), "Conflict (both deleted)")
    FileStatus.CONFLICT_ADDED_BY_US -> FileStatusUi(Pair('A', RedOrange), Pair('U', RedOrange), "Conflict (added by us)")
    FileStatus.CONFLICT_ADDED_BY_THEM -> FileStatusUi(Pair('U', RedOrange), Pair('A', RedOrange), "Conflict (added by them)")
    FileStatus.CONFLICT_DELETED_BY_US -> FileStatusUi(Pair('D', RedOrange), Pair('U', RedOrange), "Conflict (deleted by us)")
    FileStatus.CONFLICT_DELETED_BY_THEM -> FileStatusUi(Pair('U', RedOrange), Pair('D', RedOrange), "Conflict (deleted by them)")
    FileStatus.UNMODIFIED -> FileStatusUi(Pair(' ', Transparent), Pair(' ', Transparent), "Unmodified")
    FileStatus.ERROR -> FileStatusUi(Pair('?', Red), Pair('!', Red), "Error")
}
// @formatter:on

private data class CommandState(
    val command: GitCommand = GitCommand.Init,
    val isSelected: Boolean = false,
)

data class CommandUiState(
    val command: GitCommand = GitCommand.Init,
    val isSelected: Boolean = false
)

private fun CommandState.toUiState(): CommandUiState {
    return CommandUiState(
        command = this.command,
        isSelected =this.isSelected
    )
}

sealed class HomeUiEvent {
    data class ShowSnackBar(val message: String) : HomeUiEvent()
}

class HomeViewModel(private val workingDirectory: Path) : ViewModel() {
    private val _homeState = MutableStateFlow(HomeState())
    val homeUiState = _homeState
        .map { it.toUiState() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = HomeUiState()
        )

    private val _homeUiEvent = MutableSharedFlow<HomeUiEvent>()
    val homeUiEvent = _homeUiEvent.asSharedFlow()


    fun checkActiveBranch() {
        viewModelScope.launch(Dispatchers.IO)
        {
            val repo = repoFind(workingDirectory)
            val activeBranch = if (repo == null) null else getActiveBranch(repo)
            if (_homeState.value.activeBranch != activeBranch) {
                _homeState.update { currentState ->
                    currentState.copy(activeBranch = activeBranch)
                }
            }
        }
    }

    fun deleteGitRepository() {
        val gitDir = workingDirectory.resolve(".git")
        try {
            repoDelete(gitDir)
            checkActiveBranch()
            clearFilesStatus()
        } catch (e: FileNotFoundException) {
            Log.d("HomeScreen_DeleteButton", "Try to delete git dir at $gitDir, but ${e.message}")
            viewModelScope.launch {
                _homeUiEvent.emit(ShowSnackBar("Git repo is already deleted"))
            }

        } catch (e: IOException) {
            Log.wtf(
                "HomeScreen_DeleteButton",
                "IO Exception when trying to delete git repo at path $gitDir, initial error : ${e.message}"
            )
            viewModelScope.launch {
                _homeUiEvent.emit(ShowSnackBar("Deletion failed: Internal error"))
            }
        }
    }

    fun executeCurrentCommand() {
        val command = _homeState.value.currentCommand ?: run {
            viewModelScope.launch {
                _homeUiEvent.emit(ShowSnackBar("Select a command to execute"))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO)
        {
            try {
                var needToCheckActiveBranch = false
                var needToClearStatus =
                    (_homeState.value.filesState.isNotEmpty()
                            && _homeState.value.filesState.first().status.isNotEmpty()) // only clear when status is ON
                when (command) {


                    is GitCommand.Init -> {
                        JGit().init(
                            InitConfig(
                                path = workingDirectory.toString(),
                                initialBranchName = "myBranch"
                            )
                        )
                        needToCheckActiveBranch = true
                    }

                    is GitCommand.Status -> {
                        val filesStatus = JGit().status(
                            StatusConfig(
                                repoDirectory = workingDirectory.toString(),
                                filesToCheck = _homeState.value.filesState.map { it.path.toString() })
                        )
                        needToClearStatus = false
                        updateFilesStatus(filesStatus)
                    }

                    is GitCommand.Add -> {
                        val filesToAdd =
                            _homeState.value.filesState.filter { it.interactionState == FileInteractionState.SELECTED }
                                .map { it.path.toString() }

                        if (filesToAdd.isEmpty()) {
                            _homeUiEvent.emit(ShowSnackBar("Select at least one file to add"))
                        } else {
                            JGit().add(
                                AddConfig(
                                    repoDirectory = workingDirectory.toString(),
                                    filesToAdd = filesToAdd
                                )
                            )
                        }

                    }
                }

                if (needToCheckActiveBranch) checkActiveBranch()
                if (needToClearStatus) clearFilesStatus()
                resetFilesInteractionState()
                changeCurrentCommand(null)

            } catch (e: IOException) {
                Log.d(
                    "HomeScreen_ExecuteCommand",
                    "Git command on a non initiated git repo, initial error ${e.message}"
                )
                viewModelScope.launch {
                    _homeUiEvent.emit(ShowSnackBar("Git repo not initialized"))
                }
            }
        }
    }

    fun onCommandSelection(command: GitCommand) {
        val isCommandSelectedAlready =
            _homeState.value.commandsState.find { it.command == command }?.isSelected
                ?: run {
                    Log.wtf(
                        "HomeScreen_CommandSelection",
                        "Unsupported command, this should not happens"
                    )
                    viewModelScope.launch {
                        _homeUiEvent.emit(ShowSnackBar("Unsupported command"))
                    }
                    return
                }

        changeCurrentCommand(newCommand = if (isCommandSelectedAlready) null else command)

    }

    fun onFileInteraction(fileNumber: Int) {
        when (_homeState.value.filesInteractionMode) {
            FilesInteractionMode.ARE_IDLE -> return
            FilesInteractionMode.ARE_SELECTABLE -> selectFile(fileNumber)
            FilesInteractionMode.ARE_FOCUSABLE -> focusFile(fileNumber)
        }
    }


    fun modifyFileBlock(
        fileNumber: Int,
        blockNumber: Int,
        modificationFlag: BlockModificationFlag
    ) {
        val blockToModify =
            if (blockNumber == 1) _homeState.value.filesState[fileNumber].block1
            else _homeState.value.filesState[fileNumber].block2

        val updatedBlock =
            when (modificationFlag) {

                BlockModificationFlag.ADD_LINE -> {
                    if (blockToModify.size + 1 !in BlockConfig.VALID_LINE_RANGE) {
                        viewModelScope.launch {
                            _homeUiEvent.emit(ShowSnackBar("Cannot add new line, max is ${BlockConfig.MAX_LINE_NUMBER}"))
                        }
                        return
                    } else {
                        val minBlockValue =
                            max(
                                blockToModify.getOrNull(blockToModify.size - 1)
                                    ?: (BlockConfig.MIN_BLOCK_VALUE + BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE),
                                BlockConfig.MIN_BLOCK_VALUE + BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE
                            ) - BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE
                        val maxBlockValue =
                            min(
                                blockToModify.getOrNull(blockToModify.size - 1)
                                    ?: (BlockConfig.MAX_BLOCK_VALUE - BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE),
                                BlockConfig.MAX_BLOCK_VALUE - BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE
                            ) + BlockConfig.MAX_DIFF_BETWEEN_LINE_VALUE
                        blockToModify + (minBlockValue + Random.nextFloat() * (maxBlockValue - minBlockValue))
                    }
                }

                BlockModificationFlag.REMOVE_LINE -> {
                    if (blockToModify.size - 1 !in BlockConfig.VALID_LINE_RANGE) {
                        viewModelScope.launch {
                            _homeUiEvent.emit(ShowSnackBar("Cannot remove line, min is ${BlockConfig.MIN_LINE_NUMBER}"))
                        }
                        return
                    } else
                        blockToModify.dropLast(1)
                }
            }
        _homeState.update { currentState ->
            currentState.copy(filesState = currentState.filesState.mapIndexed { fileIndex, fileState ->
                if (fileIndex == fileNumber) {

                    if (blockNumber == 1) fileState.copy(block1 = updatedBlock)
                    else fileState.copy(block2 = updatedBlock)
                } else fileState

            }
            )
        }
        writeFileBlocks(fileNumber)
    }

    private fun changeCurrentCommand(newCommand: GitCommand?) {
        resetFilesInteractionState()

        _homeState.update { currentState ->
            currentState.copy(
                currentCommand = newCommand,
                filesInteractionMode = getFilesInteractionModeFromCommand(newCommand),
                commandsState =
                    if (newCommand == null) currentState.commandsState.map { it.copy(isSelected = false) }
                    else {
                        currentState.commandsState.map { commandState ->
                            if (commandState.command == newCommand) commandState.copy(isSelected = true) else
                                commandState.copy(isSelected = false)
                        }
                    }
            )
        }
    }

    private fun initializeFilesState() {
        val filteredFiles =
            workingDirectory.listDirectoryEntries().filter { !it.name.endsWith(".git") }
        _homeState.update { current ->
            current.copy(filesState = filteredFiles.mapIndexed { fileIndex, filePath ->
                FileState(
                    path = filePath,
                    fileIndex = fileIndex
                )
            })
        }
    }

    private fun initializeCommandsState(
    ) {
        _homeState.update { currentState ->
            currentState.copy(commandsState = GitCommand.ALL_COMMANDS.map {
                CommandState(
                    command = it,
                    isSelected = false,
                )
            })
        }
    }

    private fun checkFileBlocks(fileNumber: Int) {

        viewModelScope.launch(Dispatchers.IO)
        {
            val blocks =
                _homeState.value.filesState[fileNumber].path.readText().split("#")
                    .filter { s -> s.isNotEmpty() }
            val block1 = blocks[0].split("\n").filter { s -> s.isNotEmpty() }
                .map { blockValue -> blockValue.toFloat() }
            val block2 = blocks[1].split("\n").filter { s -> s.isNotEmpty() }
                .map { blockValue -> blockValue.toFloat() }
            _homeState.update { currentState ->
                currentState.copy(filesState = currentState.filesState.mapIndexed { fileIndex, fileState ->
                    if (fileIndex == fileNumber) {
                        fileState.copy(block1 = block1, block2 = block2)
                    } else fileState
                }
                )
            }
        }

    }

    private fun selectFile(fileNumber: Int) {
        _homeState.update { currentState ->
            currentState.copy(filesState = currentState.filesState.mapIndexed { fileIndex, fileState ->
                if (fileIndex == fileNumber) {
                    fileState.copy(
                        interactionState =
                            if (fileState.interactionState == FileInteractionState.SELECTED) FileInteractionState.IDLE else FileInteractionState.SELECTED
                    )
                } else fileState

            })
        }
    }

    private fun focusFile(fileNumber: Int) {
        _homeState.update { currentState ->
            currentState.copy(filesState = currentState.filesState.mapIndexed { fileIndex, fileState ->
                if (fileIndex == fileNumber) {
                    fileState.copy(
                        interactionState =
                            if (fileState.interactionState == FileInteractionState.FOCUSED) FileInteractionState.IDLE else FileInteractionState.FOCUSED
                    )
                } else {
                    if (fileState.interactionState == FileInteractionState.FOCUSED) fileState.copy(
                        interactionState =
                            FileInteractionState.IDLE
                    ) else fileState

                }
            }
            )
        }
    }

    private fun writeFileBlocks(fileNumber: Int) {
        viewModelScope.launch(Dispatchers.IO)
        {
            _homeState.value.filesState[fileNumber].let {
                it.path.writeText("#\n")
                it.block1.forEach { blockValue -> it.path.appendText("$blockValue\n") }
                it.path.appendText("#\n")
                it.block2.forEach { blockValue -> it.path.appendText("$blockValue\n") }
            }
        }

    }

    private fun resetFilesInteractionState() {
        _homeState.update { currentState ->
            currentState.copy(filesState = currentState.filesState.map {
                it.copy(
                    interactionState =
                        FileInteractionState.IDLE
                )
            }
            )
        }
    }


    private fun getFilesInteractionModeFromCommand(command: GitCommand?): FilesInteractionMode {
        return when (command) {
            is GitCommand.Init -> FilesInteractionMode.ARE_FOCUSABLE
            is GitCommand.Status -> FilesInteractionMode.ARE_FOCUSABLE
            is GitCommand.Add -> FilesInteractionMode.ARE_SELECTABLE
            null -> FilesInteractionMode.ARE_FOCUSABLE
        }
    }

    private suspend fun updateFilesStatus(filesStatus: List<FileStatus>) {
        if (_homeState.value.filesState.size != filesStatus.size) {
            Log.wtf(
                "HomeScreenViewModel_updateFilesStatus",
                "Could not update file status, number of status does not match the number of files."
            )
            _homeUiEvent.emit(ShowSnackBar("Internal error: could not find files status"))
        } else {
            _homeState.update { currentState ->
                currentState.copy(filesState = currentState.filesState.mapIndexed { fileIndex, fileStatus ->
                    fileStatus.copy(status = listOf(filesStatus[fileIndex]))
                })
            }

        }
    }

    private fun clearFilesStatus() {
        _homeState.update { currentState ->
            currentState.copy(filesState = currentState.filesState.map {
                it.copy(
                    status = listOf()
                )
            })
        }
    }

    private fun createTestsFiles() {
        val generateRandomBlockValue = { 0.1f + Random.nextFloat() * 0.9f }
        if (workingDirectory.listDirectoryEntries().size <= 1) {
            workingDirectory.resolve("test.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test1.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test2.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test3.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test4.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test5.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test6.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test7.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test8.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
            workingDirectory.resolve("test9.txt")
                .writeText("#\n${generateRandomBlockValue()}\n#\n${generateRandomBlockValue()}\n")
        }
    }

    private fun createWorkingDirectory() {
        if (workingDirectory.notExists()) workingDirectory.createDirectories()
    }

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    createWorkingDirectory()
                    createTestsFiles()
                    checkActiveBranch()
                    initializeFilesState()
                    initializeCommandsState()
                    for (fileIndex in 0..<_homeState.value.filesState.size) {
                        checkFileBlocks(fileIndex)
                    }
                }
            } catch (e: IOException) {
                Log.wtf(
                    "HomeScreenViewModel_initialisation",
                    "Could not read files in working directory initial error : ${e.message}"
                )
                _homeUiEvent.emit(ShowSnackBar("Could not read files in working directory"))
            }
        }
    }


    companion object {

        const val COMMAND_CHOOSER_NB_ROWS = 2
        const val COMMAND_CHOOSER_NB_COLS = 2

        fun provideFactory(workingDirectory: Path): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(workingDirectory)
                }
            }
    }
}