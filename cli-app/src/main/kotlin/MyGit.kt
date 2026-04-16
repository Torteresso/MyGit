import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import gitLogic.JGit
import gitLogic.add
import gitLogic.catFile
import gitLogic.checkIgnore
import gitLogic.checkout
import gitLogic.commit
import gitLogic.hashObject
import gitLogic.log
import gitLogic.lsFiles
import gitLogic.lsTree
import gitLogic.remove
import gitLogic.revParse
import gitLogic.showRef
import gitLogic.status
import gitLogic.tag
import java.io.IOException


val gitCommands = JGit()

class MGit : CliktCommand() {
    override fun run() = Unit
}

class Init : CliktCommand(name = "init") {
    val path: String by argument().default("./")
    override fun help(context: Context) =
        "Create an empty Git repository or reinitialize an existing one"

    override fun run() {
        gitCommands.init(path)
    }
}

class CatFile : CliktCommand(name = "cat-file") {
    val type: String by argument(help = "Specify the type")
        .choice("blob", "commit", "tag", "tree")
    val objectName: String by argument(name = "object", help = "The object to display")

    override fun help(context: Context) =
        "Provide content of repository objects"

    override fun run() {
        catFile(type, objectName)
    }
}

class HashObject : CliktCommand(name = "hash-object") {
    val type: String by option("-t", help = "Specify the type").choice(
        "blob",
        "commit",
        "tag",
        "tree"
    )
        .default("blob")

    val write: Boolean by option("-w", help = "Actually write the object into the database")
        .flag(default = false)
    val path: String by argument(help = "Read object from <file>")

    override fun help(context: Context) =
        "Compute object ID and optionally creates a blob from a file"

    override fun run() {
        hashObject(type, write, path)
    }
}

class Log : CliktCommand(name = "log") {
    val commit: String by argument(help = "Commit to start at.").default("HEAD")

    override fun help(context: Context) =
        "Display history of a given commit."

    override fun run() {
        log(commit)
    }
}

class LsTree : CliktCommand(name = "ls-tree") {


    val recursive: Boolean by option("-r", help = "Recurse into sub-trees")
        .flag(default = false)
    val tree: String by argument(help = "A tree-ish object.")

    override fun help(context: Context) =
        "Pretty-print a tree object."

    override fun run() {
        lsTree(recursive, tree)
    }
}

class Checkout : CliktCommand(name = "checkout") {

    val commit: String by argument(help = "The commit or tree to checkout.")
    val path: String by argument(help = "The EMPTY directory to checkout on.")

    override fun help(context: Context) =
        "Checkout a commit inside of a directory."

    override fun run() {
        checkout(commit, path)
    }
}

class ShowRef : CliktCommand(name = "show-ref") {

    override fun help(context: Context) =
        "List references."

    override fun run() {
        showRef()
    }
}

class Tag : CliktCommand(name = "tag") {

    val createTagObject: Boolean by option("-a", help = "Whether to create a tag object")
        .flag(default = false)
    val name: String? by argument(help = "The new tag's name").optional()
    val obj by argument("object", help = "The object the new tag will point to").default("HEAD")

    override fun help(context: Context) =
        "List and create tags."

    override fun run() {
        tag(createTagObject, name, obj)
    }
}

class RevParse : CliktCommand(name = "rev-parse") {

    val type: String? by option("--mgit-type", help = "Specify the  expected type")
        .choice("blob", "commit", "tag", "tree")

    val name: String by argument(help = "The new tag's name")

    override fun help(context: Context) =
        "Parse revision (or other objects) identifiers"

    override fun run() {
        revParse(type, name)
    }
}

class LsFiles : CliktCommand(name = "ls-files") {

    val verbose: Boolean by option("--verbose", help = "Show everything").flag(default = false)

    override fun help(context: Context) =
        "List all the stage files"

    override fun run() {
        lsFiles(verbose)
    }
}


class CheckIgnore : CliktCommand(name = "check-ignore") {

    val paths: List<String> by argument("path", help = "Paths to check").multiple()

    override fun help(context: Context) =
        "Check path(s) against ignore rules."

    override fun run() {
        checkIgnore(paths)
    }
}


class Status : CliktCommand(name = "status") {


    override fun help(context: Context) =
        "Show the working tree status."

    override fun run() {
        status()
    }
}


class Remove : CliktCommand(name = "rm") {

    val paths: List<String> by argument("path", help = "Paths to remove").multiple()

    override fun help(context: Context) =
        "Remove files from the working tree and the index."

    override fun run() {
        remove(paths)
    }
}


class Add : CliktCommand(name = "add") {

    val paths: List<String> by argument("path", help = "Paths to add").multiple()

    override fun help(context: Context) =
        "Add files contents to the index."

    override fun run() {
        add(paths)
    }
}

class Commit : CliktCommand(name = "commit") {

    val message: String by option("-m", help = "Message to associate with this commit").required()

    override fun help(context: Context) =
        "Record changes to the repository."

    override fun run() {
        commit(message)
    }
}

fun main(args: Array<String>) = try {
    MGit()
        .subcommands(Init())
        .subcommands(CatFile())
        .subcommands(HashObject())
        .subcommands(Log())
        .subcommands(LsTree())
        .subcommands(Checkout())
        .subcommands(ShowRef())
        .subcommands(Tag())
        .subcommands(RevParse())
        .subcommands(LsFiles())
        .subcommands(CheckIgnore())
        .subcommands(Status())
        .subcommands(Remove())
        .subcommands(Add())
        .subcommands(Commit())
        .main(args)
} catch (e: IOException) {
    System.err.println("IOException at ${e.stackTrace.first().lineNumber}: ${e.message}")
} catch (e: IllegalArgumentException) {
    System.err.println("IllegalArgumentException at ${e.stackTrace.first().lineNumber}: ${e.message}")
}

