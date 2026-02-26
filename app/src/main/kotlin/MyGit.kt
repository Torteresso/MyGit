import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.Context
import org.apache.commons.configuration2.INIConfiguration
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
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
        println("GitRepository created")
        gitdir = worktree.resolve(".git")
        println(gitdir)

        require((force || this.gitdir.isDirectory())) {"$gitdir is not a Git repository"}

        val cf: Path? = repoFile(this, Paths.get("config"))

        if (cf != null && cf.isReadable())
        {

        }
        else if (!force)
        {
            throw IOException("Configuration file missing")
        }

        if (!force)
        {
            val vers = this.conf.getProperty("core.repositoryformatversion")
            require(vers == 0) {"Unsupported repositoryformatversion: $vers" }

        }



    }
}

fun repoPath(repo: GitRepository, vararg path: Path): Path
{
    return Paths.get(repo.gitdir.toString(), *(path.map {it.toString()}.toTypedArray()))
}

fun repoFile(repo: GitRepository, vararg path: Path, mkdir: Boolean = false): Path?
{
    return if (repoDir(repo, *(path.dropLast(1).toTypedArray()), mkdir=mkdir) != null)
    {
        repoPath(repo, *path)
    }
    else
    {
        null
    }
}

fun repoDir(repo: GitRepository, vararg path: Path, mkdir: Boolean = false): Path?
{
    val path = repoPath(repo, *path)

    if (path.exists()) {
        if (path.isDirectory()) {
            return path
        }
            else
            {
                throw IOException("Not a directory $path")
            }

    }

    if (mkdir) {
        path.createDirectories()
        return path
    }
    else {
        return null
    }
}

fun repoCreate(path: Path): GitRepository
{
    val repo = GitRepository(path, true)


    if (repo.worktree.exists()) {
        require(repo.worktree.isDirectory()) { "$path is not a directory" }
        require(!repo.gitdir.exists() || repo.gitdir.listDirectoryEntries().isEmpty()) { "$path is not empty" }

    }
    else {
        repo.worktree.createDirectories()
    }

    repoDir(repo, Paths.get("branches"), mkdir = true)
    repoDir(repo, Paths.get("objects"), mkdir = true)
    repoDir(repo, Paths.get("refs", "tags"), mkdir = true)
    repoDir(repo, Paths.get("refs", "heads"), mkdir = true)

    return repo
}

class MGit : CliktCommand() {
    override fun run() = Unit
}

class Init : CliktCommand() {
    override fun help(context: Context) =
        "Create an empty Git repository or reinitialize an existing one"

    override fun run() {
        println("Executing mgit init...")

        repoCreate(Paths.get("./"))

    }
}


fun main(args: Array<String>) = MGit()
    .subcommands(Init())
    .main(args)