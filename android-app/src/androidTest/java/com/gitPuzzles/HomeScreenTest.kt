package com.gitPuzzles

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitPuzzles.ui.BlockConfig
import com.gitPuzzles.ui.BranchConfig
import com.gitPuzzles.ui.CommandChooser
import com.gitPuzzles.ui.CommandUiState
import com.gitPuzzles.ui.FileCard
import com.gitPuzzles.ui.FileColor
import com.gitPuzzles.ui.FileInteractionState
import com.gitPuzzles.ui.FileUiState
import com.gitPuzzles.ui.GridOfAllCommands
import com.gitPuzzles.ui.HomeScreen
import com.gitPuzzles.ui.HomeScreenStatusBar
import com.gitPuzzles.ui.HomeViewModel
import com.gitPuzzles.ui.SingleFileBlock
import com.gitPuzzles.ui.toUi
import gitLogic.FileStatus
import gitLogic.GitCommand
import org.junit.Rule
import org.junit.Test


class TopAppBarTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun deleteButton_onClick_triggersCallBack() {
        var clicked = false
        composeTestRule.setContent {
            HomeScreenStatusBar(
                activeBranch = "testBranch",
                onDeleteButtonClick = { clicked = true })
        }

        composeTestRule
            .onNodeWithContentDescription(composeTestRule.activity.getString(R.string.statusBar_removeGitRepo_button))
            .performClick()

        assert(clicked)
    }

    @Test
    fun activeBranch_whenNoRepo_displayAppropriateMessage() {
        composeTestRule.setContent {
            HomeScreenStatusBar(
                activeBranch = null,
                onDeleteButtonClick = {})
        }

        composeTestRule
            .onNodeWithText(composeTestRule.activity.getString(R.string.statusBar_text_noGitRepo))
            .assertExists()
    }

    @Test
    fun activeBranch_whenVeryLong_doNotCollideWithIcon() {
        val branchName = "veryLongLongLongLongLongLongLongLongLongLongBranch"
        composeTestRule.setContent {
            HomeScreenStatusBar(
                activeBranch = branchName,
                onDeleteButtonClick = {})
        }


        val textBounds = composeTestRule
            .onNodeWithText(branchName)
            .getUnclippedBoundsInRoot()

        val buttonBounds = composeTestRule
            .onNodeWithContentDescription(
                composeTestRule.activity.getString(R.string.statusBar_removeGitRepo_button)
            )
            .getUnclippedBoundsInRoot()

        assert(textBounds.right < buttonBounds.left)
    }

}

class GridOfAllCommandsTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeCommands = listOf(
        CommandUiState(command = GitCommand.Init, isSelected = false),
        CommandUiState(command = GitCommand.Add, isSelected = true),
    )

    @Test
    fun commands_areDisplayed() {
        composeTestRule.setContent {
            GridOfAllCommands(
                commandsUiState = fakeCommands,
                onDismissRequest = {},
                onCommandButtonClick = {}
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(GitCommand.Add.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(GitCommand.Status.name).assertIsNotDisplayed()
    }

    @Test
    fun commandButton_onClick_triggersCallbackWithCorrectCommand() {
        var clickedCommand: GitCommand? = null
        composeTestRule.setContent {
            GridOfAllCommands(
                commandsUiState = fakeCommands,
                onDismissRequest = {},
                onCommandButtonClick = { clickedCommand = it }
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).performClick()
        assert(clickedCommand == GitCommand.Init)
    }

    @Test
    fun commandButton_onClick_triggerDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            GridOfAllCommands(
                commandsUiState = fakeCommands,
                onDismissRequest = { dismissed = true },
                onCommandButtonClick = {}
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).performClick()
        assert(dismissed)
    }

    @Test
    fun selectedCommand_isMarkedAsSelected() {
        composeTestRule.setContent {
            GridOfAllCommands(
                commandsUiState = fakeCommands,
                onDismissRequest = {},
                onCommandButtonClick = {}
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Add.name)
            .assertIsSelected()
        composeTestRule.onNodeWithText(GitCommand.Init.name)
            .assertIsNotSelected()
    }

    @Test
    fun emptyListOfCommands_doesNotCrash() {
        composeTestRule.setContent {
            GridOfAllCommands(
                commandsUiState = emptyList(),
                onDismissRequest = {},
                onCommandButtonClick = {}
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertDoesNotExist()
    }

    @Test
    fun commandButton_onClick_othersCommandsBecomeUnselected() {

        val viewModel = HomeViewModel(
            workingDirectory = composeTestRule.activity.applicationContext.filesDir.resolve("testGitFolder")
                .toPath()
        )
        composeTestRule.setContent {
            val state by viewModel.homeUiState.collectAsStateWithLifecycle()
            GridOfAllCommands(
                commandsUiState = state.commandsUiState,
                onCommandButtonClick = { viewModel.onCommandSelection(it) },
                onDismissRequest = {}
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Add.name).performClick().assertIsSelected()
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertIsNotSelected()
    }
}


class CommandChooserTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeCommands = listOf(
        CommandUiState(command = GitCommand.Init, isSelected = false),
        CommandUiState(command = GitCommand.Add, isSelected = true),
        CommandUiState(command = GitCommand.Status, isSelected = false),
    )

    @Test
    fun commands_areDisplayed() {
        composeTestRule.setContent {
            CommandChooser(
                commandsUiState = fakeCommands,
                onCommandClick = {},
                onMoreCommandsClick = {},
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(GitCommand.Add.name).assertIsDisplayed()
        composeTestRule.onNodeWithText(GitCommand.Status.name).assertIsDisplayed()
    }

    @Test
    fun commandButton_onClick_triggersCallbackWithCorrectCommand() {
        var clickedCommand: GitCommand? = null
        composeTestRule.setContent {
            CommandChooser(
                commandsUiState = fakeCommands,
                onCommandClick = { clickedCommand = it },
                onMoreCommandsClick = {},
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).performClick()
        assert(clickedCommand == GitCommand.Init)
    }

    @Test
    fun selectedCommand_isMarkedAsSelected() {
        composeTestRule.setContent {
            CommandChooser(
                commandsUiState = fakeCommands,
                onCommandClick = { },
                onMoreCommandsClick = {},
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Add.name)
            .assertIsSelected()
        composeTestRule.onNodeWithText(GitCommand.Init.name)
            .assertIsNotSelected()
    }

    @Test
    fun emptyListOfCommands_doesNotCrash() {
        composeTestRule.setContent {
            CommandChooser(
                commandsUiState = listOf(),
                onCommandClick = { },
                onMoreCommandsClick = {},
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertDoesNotExist()
    }

    @Test
    fun commandButton_onClick_othersCommandsBecomeUnselected() {

        val viewModel = HomeViewModel(
            workingDirectory = composeTestRule.activity.applicationContext.filesDir.resolve("testGitFolder")
                .toPath()
        )
        composeTestRule.setContent {
            val state by viewModel.homeUiState.collectAsStateWithLifecycle()
            CommandChooser(
                commandsUiState = state.commandsUiState,
                onCommandClick = { viewModel.onCommandSelection(it) },
                onMoreCommandsClick = {},
            )
        }
        composeTestRule.onNodeWithText(GitCommand.Add.name).performClick().assertIsSelected()
        composeTestRule.onNodeWithText(GitCommand.Init.name).assertIsNotSelected()
    }
}

class FileSystemTests {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val fakeFiles = listOf(
        FileUiState(
            color = FileColor.BLUE,
            interactionState = FileInteractionState.IDLE,
            status = listOf(FileStatus.UNTRACKED.toUi()),
            block1 = List(BlockConfig.MIN_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE },
            block2 = List(BlockConfig.MAX_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE }
        ),
        FileUiState(
            color = FileColor.BROWN,
            interactionState = FileInteractionState.FOCUSED,
            status = listOf(FileStatus.ADDED.toUi()),
            block1 = List(BlockConfig.MIN_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE },
            block2 = List(BlockConfig.MAX_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE }
        ),
        FileUiState(
            color = FileColor.PURPLE,
            interactionState = FileInteractionState.SELECTED,
            status = listOf(FileStatus.DELETED_UNSTAGED.toUi()),
            block1 = List(BlockConfig.MIN_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE },
            block2 = List(BlockConfig.MAX_LINE_NUMBER) { BlockConfig.MIN_BLOCK_VALUE }
        ),
    )

    @Test
    fun singleFileBlock_hasCorrectNumberOfLines() {
        composeTestRule.setContent {
            SingleFileBlock(
                block = fakeFiles[0].block1,
                fileColor = fakeFiles[0].color,
                modifier = Modifier.size(200.dp, 300.dp)
            )
        }
        composeTestRule.onAllNodesWithContentDescription("block_line")
            .assertCountEquals(fakeFiles[0].block1.size)
    }

    @Test
    fun fileCard_whenFocused_hasModificationButtons() {
        val fileNumber = 1
        composeTestRule.setContent {
            FileCard(
                fileNumber = fileNumber,
                fileUiState = fakeFiles[fileNumber],
                onFileClick = {},
                onBlockModificationButtonClick = { _, _, _ -> }
            )
        }
        composeTestRule.onNodeWithContentDescription(
            "${composeTestRule.activity.getString(R.string.fileBlock_addBlockButton_description)} 1"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "${composeTestRule.activity.getString(R.string.fileBlock_addBlockButton_description)} 2"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "${composeTestRule.activity.getString(R.string.fileBlock_removeBlockButton_description)} 1"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(
            "${composeTestRule.activity.getString(R.string.fileBlock_removeBlockButton_description)} 2"
        ).assertIsDisplayed()

    }

    @Test
    fun fileCard_whenIdle_doesNotHaveModificationButtons() {
        val fileNumber = 0
        composeTestRule.setContent {
            FileCard(
                fileNumber = fileNumber,
                fileUiState = fakeFiles[fileNumber],
                onFileClick = {},
                onBlockModificationButtonClick = { _, _, _ -> }
            )
        }
        composeTestRule.onAllNodesWithContentDescription(
            composeTestRule.activity.getString(R.string.fileBlock_addBlockButton_description)
        ).assertCountEquals(0)
        composeTestRule.onAllNodesWithContentDescription(
            composeTestRule.activity.getString(R.string.fileBlock_removeBlockButton_description)
        ).assertCountEquals(0)
    }
}

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    fun executeCommand(gitCommand: GitCommand) {
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.moreCommandButton_text))
            .performClick()

        composeTestRule.onNode(
            hasText(gitCommand.name) and hasAnyAncestor(
                hasTestTag(
                    composeTestRule.activity.getString(R.string.testTag_commandChooser)
                )
            )
        )
            .performClick()

        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.commandBar_execution_button))
            .performClick()

    }

    @Test
    fun initCommand_onExecution_updateActiveBranch() {

        val viewModel = HomeViewModel(
            workingDirectory = composeTestRule.activity.applicationContext.filesDir.resolve("testGitFolder")
                .toPath()
        )
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.statusBar_text_noGitRepo))
            .assertIsDisplayed()

        executeCommand(GitCommand.Init)

        composeTestRule.onNodeWithText(BranchConfig.DEFAULT_NAME).assertIsDisplayed()
    }

    @Test
    fun addCommandAndStatusCommand_onExecution_updateFileStatus() {

        val viewModel = HomeViewModel(
            workingDirectory = composeTestRule.activity.applicationContext.filesDir.resolve("testGitFolder")
                .toPath()
        )
        composeTestRule.setContent {
            HomeScreen(viewModel = viewModel)
        }

        val fileToAddIndex = 0

        executeCommand(GitCommand.Init)

        executeCommand(GitCommand.Status)

        composeTestRule.onNodeWithTag("${composeTestRule.activity.getString(R.string.testTag_fileNumber)} $fileToAddIndex")
            .assert(
                hasAnyChild(hasText(FileStatus.UNTRACKED.toUi().statusCodeY.first.toString()))
            )

        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.moreCommandButton_text))
            .performClick()

        composeTestRule.onNode(
            hasText(GitCommand.Add.name) and hasAnyAncestor(
                hasTestTag(
                    composeTestRule.activity.getString(R.string.testTag_commandChooser)
                )
            )
        )
            .performClick()

        composeTestRule.onNodeWithText(numberToLetter(fileToAddIndex))
            .performClick()

        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.commandBar_execution_button))
            .performClick()

        composeTestRule.onNodeWithTag("${composeTestRule.activity.getString(R.string.testTag_fileNumber)} $fileToAddIndex")
            .assert(
                hasAnyChild(hasText(FileStatus.ADDED.toUi().statusCodeX.first.toString()))
            ).assert(
                !hasAnyChild(hasText(FileStatus.ADDED_MODIFIED.toUi().statusCodeY.first.toString()))
            )

        composeTestRule.onNodeWithText(numberToLetter(fileToAddIndex))
            .performClick()

        val blockNumberToModify = 1
        composeTestRule.onNodeWithContentDescription("${composeTestRule.activity.getString(R.string.fileBlock_addBlockButton_description)} $blockNumberToModify")
            .performClick()

        executeCommand(GitCommand.Status)

        composeTestRule.onNodeWithTag("${composeTestRule.activity.getString(R.string.testTag_fileNumber)} $fileToAddIndex")
            .assert(
                hasAnyChild(hasText(FileStatus.ADDED_MODIFIED.toUi().statusCodeX.first.toString()))
                        and hasAnyChild(hasText(FileStatus.ADDED_MODIFIED.toUi().statusCodeY.first.toString()))
            )
    }
}

