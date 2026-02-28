import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import org.apache.commons.configuration2.INIConfiguration
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries

data class GitRepository(val worktree: Path, val force: Boolean = false) {
    val gitdir: Path
    var conf: INIConfiguration = INIConfiguration()

    init {
        gitdir = worktree.resolve(".git")

        require((force || this.gitdir.isDirectory())) { "$gitdir is not a Git repository" }

        val cf: Path? = repoFile(this, Paths.get("config"))

        if (cf != null && cf.isReadable()) {
            FileReader(cf.toFile()).use { reader -> this.conf.read(reader)}
        } else if (!force) {
            throw IOException("Configuration file missing")
        }

        if (!force) {
            val vers = this.conf.getProperty("core.repositoryformatversion")
            require(vers == "0") { "Unsupported repositoryformatversion: $vers" }
        }


    }
}

fun repoPath(repo: GitRepository, vararg path: Path): Path {
    return Paths.get(repo.gitdir.toString(), *(path.map { it.toString() }.toTypedArray()))
}

fun repoFile(repo: GitRepository, vararg path: Path, mkdir: Boolean = false): Path? {
    return if (repoDir(repo, *(path.dropLast(1).toTypedArray()), mkdir = mkdir) != null) {
        repoPath(repo, *path)
    } else {
        null
    }
}

fun repoDir(repo: GitRepository, vararg path: Path, mkdir: Boolean = false): Path? {
    val path = repoPath(repo, *path)

    if (path.exists()) {
        if (path.isDirectory()) {
            return path
        } else {
            throw IOException("Not a directory $path")
        }

    }

    if (mkdir) {
        path.createDirectories()
        return path
    } else {
        return null
    }
}

fun repoDefaultConfig(): INIConfiguration
{
    val config = INIConfiguration()
    val coreSection = config.getSection("core")  // crée la section si elle n'existe pas
    coreSection.setProperty("repositoryformatversion", "0")
    coreSection.setProperty("filemode", "false")
    coreSection.setProperty("bare", "false")
    return config
}

fun repoCreate(path: Path): GitRepository {
    val repo = GitRepository(path, true)


    if (repo.worktree.exists()) {
        require(repo.worktree.isDirectory()) { "$path is not a directory" }
        require(
            !repo.gitdir.exists() || repo.gitdir.listDirectoryEntries().isEmpty()
        ) { "${path.absolute()} is not empty" }

    } else {
        repo.worktree.createDirectories()
    }

    repoDir(repo, Paths.get("branches"), mkdir = true)
    repoDir(repo, Paths.get("objects"), mkdir = true)
    repoDir(repo, Paths.get("refs", "tags"), mkdir = true)
    repoDir(repo, Paths.get("refs", "heads"), mkdir = true)

    val descriptionFile = File(repoFile(repo, Paths.get("description")).toString())
    descriptionFile.writeText("unnamed repository; edit this file 'description' to name the repository.\n")

    val headFile = File(repoFile(repo, Paths.get("HEAD")).toString())
    headFile.writeText("ref: refs/heads/master\n")

    val configFile = File(repoFile(repo, Paths.get("config")).toString())
    val config = repoDefaultConfig()
    config.write(FileWriter(configFile))

    return repo
}

fun repoFind(path: Path = Paths.get("."), required: Boolean = true): GitRepository?
{
    val path = path.absolute()

    if (path.resolve(".git").isDirectory())
        return GitRepository(path)

   val parent = path.resolve("..").absolute()

   if (parent == path)
   {
       check(required) { "No git directory." }

       return null
   }

    return repoFind(parent, required)
}



class MGit : CliktCommand() {
    override fun run() = Unit
}

class Init : CliktCommand() {
    val path: String by argument().default("./")
    override fun help(context: Context) =
        "Create an empty Git repository or reinitialize an existing one"

    override fun run() {
        repoCreate(Paths.get(path))
    }
}


fun main(args: Array<String>) = MGit()
    .subcommands(Init())
    .main(args)