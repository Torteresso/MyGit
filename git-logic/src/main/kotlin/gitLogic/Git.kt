package gitLogic

sealed interface GitCommand {
    val name: String

    data object Init : GitCommand {
        override val name: String = "init"
    }

    data object Status : GitCommand {
        override val name: String = "status"
    }

    companion object {
        val ALL_COMMANDS: List<GitCommand> = listOf(Init, Status)
    }
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

enum class FileStatus {
    ADDED, MODIFIED_STAGED, MODIFIED_UNSTAGED,
    DELETED_STAGED, DELETED_UNSTAGED, UNTRACKED, CONFLICT, UNMODIFIED
}

interface GitCommandsFunctions {
    fun init(config: InitConfig)

    fun add(config: AddConfig)
    fun commit(config: CommitConfig)

    fun status(config: StatusConfig): List<FileStatus>
}

