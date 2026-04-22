package com.example.gitPuzzles.ui

import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.gitPuzzles.themlng.Black
import com.example.gitPuzzles.themlng.Blue
import com.example.gitPuzzles.themlng.Brown
import com.example.gitPuzzles.themlng.Green
import com.example.gitPuzzles.themlng.Olive
import com.example.gitPuzzles.themlng.Pink
import com.example.gitPuzzles.themlng.Purple
import com.example.gitPuzzles.themlng.Red
import com.example.gitPuzzles.themlng.RedOrange
import com.example.gitPuzzles.themlng.Transparent
import com.example.gitPuzzles.themlng.White
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
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.random.Random

private data class FileInternalState(
    var path: Path,
    var color: Color = White,
    var isSelected: Boolean = false,
    var isFocused: Boolean = false,
    var status: List<FileStatus> = listOf(),
    var block1: MutableList<Float> = mutableListOf(),
    var block2: MutableList<Float> = mutableListOf()
)


data class HomeUiState(
    val activeBranch: String? = null,
    val currentCommand: GitCommand = GitCommand.Init,
    val areFilesSelectable: Boolean = false,
    val filesUiState: List<FileUiState> = listOf()
)

data class FileUiState(
    val color: Color = White,
    val isSelected: Boolean = false,
    val isFocused: Boolean = false,
    val status: List<FileStatusUi> = listOf(),
    val block1: List<Float> = listOf(),
    val block2: List<Float> = listOf()
)


data class FileStatusUi(
    val statusCodeX: Pair<Char, Color> = Pair(' ', White),
    val statusCodeY: Pair<Char, Color> = Pair(' ', White),
    val label: String = "no status yet"
)

enum class BlockModificationFlag {
    ADD_LINE, REMOVE_LINE
}

object BlockConfig {
    const val MIN_LINE_NUMBER = 0
    const val MAX_LINE_NUMBER = 20
    val VALID_LINE_RANGE = MIN_LINE_NUMBER..MAX_LINE_NUMBER
}

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

sealed class HomeUiEvent {
    data class ShowSnackBar(val message: String) : HomeUiEvent()
}

class HomeViewModel(private val workingDirectory: Path) : ViewModel() {
    private val _homeUiState = MutableStateFlow(HomeUiState())
    val homeUiState = _homeUiState.asStateFlow()

    private val _homeUiEvent = MutableSharedFlow<HomeUiEvent>()
    val homeUiEvent = _homeUiEvent.asSharedFlow()

    private var filesInternalState: List<FileInternalState> = listOf()

    fun checkActiveBranch() {
        viewModelScope.launch(Dispatchers.IO)
        {
            val repo = repoFind(workingDirectory)
            val activeBranch = if (repo == null) null else getActiveBranch(repo)
            if (_homeUiState.value.activeBranch != activeBranch) {
                _homeUiState.update { currentState ->
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
                _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Git repo is already deleted"))
            }

        } catch (e: IOException) {
            Log.wtf(
                "HomeScreen_DeleteButton",
                "IO Exception when trying to delete git repo at path $gitDir, initial error : ${e.message}"
            )
            viewModelScope.launch {
                _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Deletion failed: Internal error"))
            }
        }
    }

    fun executeCurrentCommand() {
        viewModelScope.launch(Dispatchers.IO)
        {
            try {
                var needToCheckActiveBranch = false
                var needToClearStatus =
                    (filesInternalState.isNotEmpty()
                            && filesInternalState.first().status.isNotEmpty()) // only clear when status is ON
                when (_homeUiState.value.currentCommand) {


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
                                filesToCheck = filesInternalState.map { it.path.toString() })
                        )
                        needToClearStatus = false
                        updateFilesStatus(filesStatus)
                    }

                    is GitCommand.Add -> JGit().add(
                        AddConfig(
                            repoDirectory = workingDirectory.toString(),
                            filesToAdd = filesInternalState.filter { it.isSelected }
                                .map { it.path.toString() }
                        )
                    )

                }

                if (needToCheckActiveBranch) checkActiveBranch()
                if (needToClearStatus) clearFilesStatus()

            } catch (e: IOException) {
                Log.d(
                    "HomeScreen_ExecuteCommand",
                    "Git command on a non initiated git repo, initial error ${e.message}"
                )
                viewModelScope.launch {
                    _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Git repo not initialized"))
                }
            }
        }
    }

    fun changeCurrentCommand(newCommand: GitCommand) {
        _homeUiState.update { currentState ->
            currentState.copy(
                currentCommand = newCommand,
                areFilesSelectable = doesCommandNeedFiles(newCommand)
            )
        }
    }

    fun initializeFileStatus() {
        val filteredFiles =
            workingDirectory.listDirectoryEntries().filter { !it.name.endsWith(".git") }
        filesInternalState = filteredFiles.mapIndexed { fileIndex, filePath ->
            FileInternalState(
                path = filePath,
                color = fileIndexToColorMap.getValue(fileIndex % fileIndexToColorMap.size)
            )
        }
        _homeUiState.update { currentState ->
            currentState.copy(
                filesUiState = filesInternalState.map { FileUiState(color = it.color) }
            )
        }
    }

    fun selectFile(fileNumber: Int) {
        filesInternalState[fileNumber].apply { isSelected = !isSelected }
        _homeUiState.update { currentState ->
            currentState.copy(
                filesUiState = currentState.filesUiState.mapIndexed { fileIndex, fileState ->
                    if (fileIndex == fileNumber)
                        fileState.copy(isSelected = !fileState.isSelected) else fileState
                }
            )
        }
    }

    fun focusFile(fileNumber: Int) {
        filesInternalState.mapIndexed { fileIndex, fileState ->
            if (fileIndex == fileNumber) fileState.isFocused = !fileState.isFocused
            else fileState.isFocused = false
        }
        _homeUiState.update { currentState ->
            currentState.copy(
                filesUiState = currentState.filesUiState.mapIndexed { fileIndex, fileState ->
                    if (fileIndex == fileNumber)
                        fileState.copy(isFocused = !fileState.isFocused) else fileState.copy(
                        isFocused = false
                    )
                }
            )
        }
    }

    fun modifyFileBlock(
        fileNumber: Int,
        blockNumber: Int,
        modificationFlag: BlockModificationFlag
    ) {
        var blockToModify: MutableList<Float>

        filesInternalState[fileNumber].let {
            blockToModify = if (blockNumber == 1) it.block1 else it.block2

            when (modificationFlag) {
                BlockModificationFlag.ADD_LINE -> {
                    if (blockToModify.size + 1 !in BlockConfig.VALID_LINE_RANGE) {
                        viewModelScope.launch {
                            _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Cannot add new line, max is ${BlockConfig.MAX_LINE_NUMBER}"))
                        }
                        return
                    } else
                        blockToModify.add(0.1f + Random.nextFloat() * 0.9f)
                }


                BlockModificationFlag.REMOVE_LINE -> {
                    if (blockToModify.size - 1 !in BlockConfig.VALID_LINE_RANGE) {
                        viewModelScope.launch {
                            _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Cannot remove line, min is ${BlockConfig.MIN_LINE_NUMBER}"))
                        }
                        return
                    } else
                        blockToModify.removeAt(blockToModify.lastIndex)
                }
            }
        }
        writeFileBlocks(fileNumber)

        _homeUiState.update { currentState ->
            currentState.copy(
                filesUiState = currentState.filesUiState.mapIndexed { fileIndex, fileState ->
                    if (fileIndex == fileNumber) {
                        if (blockNumber == 1) fileState.copy(block1 = blockToModify.toList())
                        else fileState.copy(block2 = blockToModify.toList())
                    } else fileState
                }
            )
        }
    }

    private fun checkFileBlocks(fileNumber: Int) {
        viewModelScope.launch(Dispatchers.IO)
        {
            var block1: List<Float>
            var block2: List<Float>
            filesInternalState[fileNumber].let {
                val blocks = it.path.readText().split("#").filter { it.isNotEmpty() }
                val test = blocks[0].split("\n")
                block1 = blocks[0].split("\n").filter { it.isNotEmpty() }
                    .map { blockValue -> blockValue.toFloat() }
                block2 = blocks[1].split("\n").filter { it.isNotEmpty() }
                    .map { blockValue -> blockValue.toFloat() }
                it.block1 = block1.toMutableList()
                it.block2 = block2.toMutableList()
            }

            _homeUiState.update { currentState ->
                currentState.copy(
                    filesUiState = currentState.filesUiState.mapIndexed { fileIndex, fileState ->
                        if (fileIndex == fileNumber) {
                            fileState.copy(block1 = block1, block2 = block2)
                        } else fileState
                    }
                )
            }
        }
    }

    private fun writeFileBlocks(fileNumber: Int) {
        viewModelScope.launch(Dispatchers.IO)
        {
            filesInternalState[fileNumber].let {
                it.path.writeText("#\n")
                it.block1.forEach { blockValue -> it.path.appendText("$blockValue\n") }
                it.path.appendText("#\n")
                it.block2.forEach { blockValue -> it.path.appendText("$blockValue\n") }
            }
        }

    }


    private fun doesCommandNeedFiles(command: GitCommand): Boolean {
        return when (command) {
            is GitCommand.Init -> false
            is GitCommand.Status -> false
            is GitCommand.Add -> true
        }
    }

    private suspend fun updateFilesStatus(filesStatus: List<FileStatus>) {
        if (filesInternalState.size != filesStatus.size) {
            Log.wtf(
                "HomeScreenViewModel_updateFilesStatus",
                "Could not update file status, number of status does not match the number of files."
            )
            _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Internal error: could not find files status"))
        } else {
            filesInternalState.forEachIndexed { fileIndex, fileStatus ->
                fileStatus.status = listOf(filesStatus[fileIndex])
            }

            _homeUiState.update { currentState ->
                currentState.copy(filesUiState = filesInternalState.map {
                    FileUiState(
                        color = it.color,
                        status = it.status.map { status -> status.toUi() })
                })
            }
        }
    }

    private fun clearFilesStatus() {
        filesInternalState.forEach { it.status = listOf() }
        _homeUiState.update { currentState ->
            currentState.copy(filesUiState = currentState.filesUiState.map {
                it.copy(
                    status = listOf()
                )
            })
        }
    }

    private fun createTestsFiles() {
        if (workingDirectory.listDirectoryEntries().size <= 1) {
            workingDirectory.resolve("test.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test1.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test2.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test3.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test4.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test5.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test6.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test7.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test8.txt").writeText("#\n0.7\n#\n0.7\n")
            workingDirectory.resolve("test9.txt").writeText("#\n0.7\n#\n0.7\n")
        }
    }

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    createTestsFiles()
                    checkActiveBranch()
                    initializeFileStatus()
                    for (fileIndex in 0..<filesInternalState.size) {
                        checkFileBlocks(fileIndex)
                    }
                }
            } catch (e: IOException) {
                Log.wtf(
                    "HomeScreenViewModel_initialisation",
                    "Could not read files in working directory initial error : ${e.message}"
                )
                _homeUiEvent.emit(HomeUiEvent.ShowSnackBar("Could not read files in working directory"))
            }
        }
    }

    private val fileIndexToColorMap = mapOf(
        0 to Blue,
        1 to Green,
        2 to Red,
        3 to Purple,
        4 to Brown,
        5 to Pink,
        6 to Black,
        7 to Olive
    )

    companion object {
        fun provideFactory(workingDirectory: Path): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(workingDirectory)
                }
            }
    }
}