package com.gitPuzzles

import com.gitPuzzles.ui.FileStatusUi
import com.gitPuzzles.ui.HomeUiEvent
import com.gitPuzzles.ui.HomeUiState
import com.gitPuzzles.ui.HomeViewModel
import com.gitPuzzles.ui.toUi
import gitLogic.FileStatus
import gitLogic.GitCommand
import gitLogic.InitConfig
import gitLogic.JGit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.notExists

class HomeViewModelTest {

    @TempDir
    lateinit var workingDirectory: Path

    @OptIn(ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setUpTestDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @AfterEach
    fun resetMainDispatchers() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkActiveBranch_repoFound_updatesActiveBranch() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            val branchName = "testBranch"
            JGit().init(
                InitConfig(
                    path = workingDirectory.toString(),
                    initialBranchName = branchName
                )
            )

            viewModel.checkActiveBranch()
            testScheduler.advanceUntilIdle()

            assertEquals(branchName, viewModel.homeUiState.value.activeBranch)
        }

    @Test
    fun checkActiveBranch_repoNotFound_updatesActiveBranch() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            viewModel.checkActiveBranch()
            testScheduler.advanceUntilIdle()

            assertEquals(null, viewModel.homeUiState.value.activeBranch)
        }

    @Test
    fun deleteGitRepo_onExistingRepo_updatesState() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            val branchName = "testBranch"
            JGit().init(
                InitConfig(
                    path = workingDirectory.toString(),
                    initialBranchName = branchName
                )
            )
            viewModel.deleteGitRepository()
            testScheduler.advanceUntilIdle()

            assertEquals(null, viewModel.homeUiState.value.activeBranch)
            viewModel.homeUiState.value.filesUiState.forEach {
                assertEquals(
                    emptyList<FileStatusUi>(),
                    it.status
                )
            }
            assertTrue(workingDirectory.resolve(".git").notExists())
        }

    @Test
    fun deleteGitRepo_onEmptyRepo_emitSnackBar() =
        initiateViewModelAndCollectStateAndEvents { viewModel, events ->
            viewModel.deleteGitRepository()
            testScheduler.advanceUntilIdle()

            assertEquals(null, viewModel.homeUiState.value.activeBranch)
            viewModel.homeUiState.value.filesUiState.forEach {
                assertEquals(
                    emptyList<FileStatusUi>(),
                    it.status
                )
            }
            assertTrue(workingDirectory.resolve(".git").notExists())
            assertTrue(events.any { it is HomeUiEvent.ShowSnackBar })
        }

    @Test
    fun executeCurrentCommand_noCommandSelected_emitsSnackBar() =
        initiateViewModelAndCollectStateAndEvents { viewModel, events ->

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            assertTrue(events.any { it is HomeUiEvent.ShowSnackBar })
        }

    @Test
    fun executeCurrentCommand_initCommand_createsGitRepo() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            assertTrue(workingDirectory.resolve(".git").exists())
        }

    @Test
    fun executeCurrentCommand_initCommand_updatesActiveBranch() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            assertNotNull(viewModel.homeUiState.value.activeBranch)
        }

    @Test
    fun executeCurrentCommand_statusCommand_updatesFilesStatus() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            JGit().init(
                InitConfig(
                    path = workingDirectory.toString(),
                    initialBranchName = "testBranch"
                )
            )

            viewModel.onCommandSelection(GitCommand.Status)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            viewModel.homeUiState.value.filesUiState.forEach {
                assertEquals(
                    FileStatus.UNTRACKED.toUi(),
                    it.status.first()
                )
            }
        }

    @Test
    fun executeCurrentCommand_addCommand_noFilesSelected_emitsSnackBar() =
        initiateViewModelAndCollectStateAndEvents { viewModel, events ->
            JGit().init(
                InitConfig(
                    path = workingDirectory.toString(),
                    initialBranchName = "testBranch"
                )
            )

            viewModel.onCommandSelection(GitCommand.Add)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            assertTrue(events.any { it is HomeUiEvent.ShowSnackBar })
        }

    @Test
    fun executeCurrentCommand_addCommand_fileSelected_updatesFilesStatus() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            JGit().init(
                InitConfig(
                    path = workingDirectory.toString(),
                    initialBranchName = "testBranch"
                )
            )
            viewModel.onCommandSelection(GitCommand.Add)
            testScheduler.advanceUntilIdle()

            viewModel.onFileInteraction(fileNumber = 0)
            viewModel.onFileInteraction(fileNumber = 1)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            viewModel.onCommandSelection(GitCommand.Status)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()


            assertEquals(
                FileStatus.ADDED.toUi(),
                viewModel.homeUiState.value.filesUiState[0].status.first()
            )
            assertEquals(
                FileStatus.ADDED.toUi(),
                viewModel.homeUiState.value.filesUiState[1].status.first()
            )
            assertEquals(
                FileStatus.UNTRACKED.toUi(),
                viewModel.homeUiState.value.filesUiState[2].status.first()
            )

        }

    @Test
    fun executeCurrentCommand_ioException_emitsSnackBar() =
        initiateViewModelAndCollectStateAndEvents { viewModel, events ->
            viewModel.onCommandSelection(GitCommand.Status)
            testScheduler.advanceUntilIdle()

            viewModel.executeCurrentCommand()
            testScheduler.advanceUntilIdle()

            assertTrue(events.any { it is HomeUiEvent.ShowSnackBar })
        }

    @Test
    fun onCommandSelection_selectTwoTimesTheSameCommand_UnselectedTheCommand() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.homeUiState.value.currentCommand)
        }

    @Test
    fun onCommandSelection_invalidCommand_emitSnackBar() =
        initiateViewModelAndCollectStateAndEvents { viewModel, _ ->
            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            viewModel.onCommandSelection(GitCommand.Init)
            testScheduler.advanceUntilIdle()

            assertNull(viewModel.homeUiState.value.currentCommand)
        }

    fun initiateViewModelAndCollectStateAndEvents(testBody: suspend TestScope.(viewModel: HomeViewModel, events: List<HomeUiEvent>) -> Unit) {
        runTest {
            val viewModel = HomeViewModel(workingDirectory, StandardTestDispatcher(testScheduler))
            testScheduler.advanceUntilIdle()
            val states = mutableListOf<HomeUiState>()
            val job1 = launch { viewModel.homeUiState.collect { states.add(it) } }

            val events = mutableListOf<HomeUiEvent>()
            val job2 = launch { viewModel.homeUiEvent.collect { events.add(it) } }

            testBody(viewModel, events)

            job1.cancel()
            job2.cancel()
        }
    }

}