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

interface GitCommandsFunctions {
    fun init(config: InitConfig)
}
