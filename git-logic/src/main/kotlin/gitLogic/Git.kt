package gitLogic

interface GitCommandsFunctions {
    fun init(path: String)
}

sealed interface GitCommand {
    abstract val name: String

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