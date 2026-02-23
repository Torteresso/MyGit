import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.Context

class MGit : CliktCommand() {
    override fun run() = Unit
}

class Init : CliktCommand() {
    override fun help(context: Context) = "Create an empty Git repository or reinitialize an existing one"
    override fun run() {
        echo("Executing mgit init...")
    }
}


fun main(args: Array<String>) = MGit()
    .subcommands(Init())
    .main(args)