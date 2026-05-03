package gitLogic

sealed interface GitCommand {
    val name: String

    data object Init : GitCommand {
        override val name: String = "init"
    }

    data object Status : GitCommand {
        override val name: String = "status"
    }

    data object Add : GitCommand {
        override val name: String = "add"
    }

    companion object {
        val ALL_COMMANDS: List<GitCommand> = listOf(Init, Add, Status)
    }
}


interface GitCommandsFunctions {
    fun init(config: InitConfig)

    fun add(config: AddConfig)
    fun commit(config: CommitConfig)

    fun status(config: StatusConfig): List<FileStatus>
}


data class InitConfig(
    val path: String,
    val initialBranchName: String? = null
)

data class AddConfig(
    val repoDirectory: String,
    val filesToAdd: List<String>
)

data class CommitConfig(
    val repoDirectory: String,
    val message: String,
)

data class StatusConfig(
    val repoDirectory: String,
    val filesToCheck: List<String>? = null,
)

/*From git doc (without Rename and copied and redundant cases) :
X          Y     Meaning
-------------------------------------------------
          [MD]   not updated
M        [ MD]   updated in index
A        [ MD]   added to index
D         [ M]   deleted from index
[MA]             index and work tree matches
-------------------------------------------------
D           D    unmerged, both deleted
A           U    unmerged, added by us
U           D    unmerged, deleted by them
U           A    unmerged, added by them
D           U    unmerged, deleted by us
A           A    unmerged, both added
U           U    unmerged, both modified
-------------------------------------------------
?           ?    untracked
!           !    ignored
-------------------------------------------------
actually DM is D + ??
*/
enum class FileStatus {
    ADDED,
    MODIFIED_STAGED,
    DELETED_STAGED,
    MODIFIED_UNSTAGED,
    DELETED_UNSTAGED,
    UNTRACKED,
    IGNORED,
    MODIFIED_STAGED_UNSTAGED,
    DELETED_STAGED_MODIFIED,
    ADDED_MODIFIED,
    ADDED_DELETED,
    MODIFIED_STAGED_DELETED,
    CONFLICT_BOTH_MODIFIED,
    CONFLICT_BOTH_ADDED,
    CONFLICT_BOTH_DELETED,
    CONFLICT_ADDED_BY_US,
    CONFLICT_ADDED_BY_THEM,
    CONFLICT_DELETED_BY_US,
    CONFLICT_DELETED_BY_THEM,
    UNMODIFIED,
    ERROR, // Special case when status could not be determined, should not happen
}

