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
import com.example.gitPuzzles.themlng.Orange
import com.example.gitPuzzles.themlng.Pink
import com.example.gitPuzzles.themlng.Purple
import com.example.gitPuzzles.themlng.Red
import com.example.gitPuzzles.themlng.White
import gitLogic.GitCommand
import gitLogic.InitConfig
import gitLogic.JGit
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
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

private data class FileInternalState(
    val path: Path,
    val color: Color = White
)

data class FileUiState(
    val color: Color = White,
)

data class HomeUiState(
    val activeBranch: String? = null,
    val needRefresh: Boolean = true,
    val currentCommand: GitCommand = GitCommand.Init,
    val filesUiState: List<FileUiState> = listOf()
)

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

            _homeUiState.update { currentState ->
                if (_homeUiState.value.activeBranch != activeBranch) {
                    currentState.copy(activeBranch = activeBranch, needRefresh = false)
                } else {
                    currentState.copy(needRefresh = false)
                }
            }
        }
    }

    fun deleteGitRepository() {
        val gitDir = workingDirectory.resolve(".git")
        try {
            repoDelete(gitDir)
            requestRefresh()
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
            when (_homeUiState.value.currentCommand) {
                is GitCommand.Init -> {
                    JGit().init(
                        InitConfig(
                            path = workingDirectory.toString(),
                            initialBranchName = "myBranch"
                        )
                    )
                }

                is GitCommand.Status -> {
                    //TODO
                }
            }
            requestRefresh()
        }
    }

    fun changeCurrentCommand(newCommand: GitCommand) {
        _homeUiState.update { currentState -> currentState.copy(currentCommand = newCommand) }
    }

    fun initializeFileStates() {
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

    private fun requestRefresh() {
        _homeUiState.update { currentState -> currentState.copy(needRefresh = true) }
    }

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    initializeFileStates()
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
        1 to Orange,
        2 to Green,
        3 to Red,
        4 to Purple,
        5 to Brown,
        6 to Pink,
        7 to Black,
        8 to Olive
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