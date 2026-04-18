package com.example.gitPuzzles.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import gitLogic.JGit
import gitLogic.getActiveBranch
import gitLogic.repoDelete
import gitLogic.repoFind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path

data class HomeUiState(val activeBranch: String? = null, val needRefresh: Boolean = true)

class HomeViewModel(private val workingDirectory: Path) : ViewModel() {
    private val _homeUiState = MutableStateFlow(HomeUiState())

    val homeUiState = _homeUiState.asStateFlow()

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
        } catch (e: IOException) {
            Log.wtf(
                "HomeScreen_DeleteButton",
                "IO Exception when trying to delete git repo at path $gitDir, initial error : ${e.message}"
            )
        }
    }

    fun initGitRepository() {
        JGit().init(workingDirectory.toString())
        requestRefresh()
    }

    private fun requestRefresh() {
        _homeUiState.update { currentState -> currentState.copy(needRefresh = true) }
    }

    companion object {
        fun provideFactory(workingDirectory: Path): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    HomeViewModel(workingDirectory)
                }
            }
    }
}