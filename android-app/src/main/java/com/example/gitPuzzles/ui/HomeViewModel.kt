package com.example.gitPuzzles.ui

import androidx.lifecycle.ViewModel
import gitLogic.getActiveBranch
import gitLogic.repoFind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path

data class HomeUiState(val activeBranch: String? = null)

class HomeViewModel : ViewModel() {
    private val _homeUiState = MutableStateFlow(HomeUiState())

    val homeUiState = _homeUiState.asStateFlow()

    fun checkActiveBranch(gitPath: Path) {
        val repo = repoFind(gitPath)
        val activeBranch = if (repo == null) null else getActiveBranch(repo)

        if (_homeUiState.value.activeBranch != activeBranch) {
            _homeUiState.update { currentState ->
                currentState.copy(activeBranch = activeBranch)
            }
        }
    }
}