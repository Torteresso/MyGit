import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import org.apache.commons.configuration2.INIConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Formatter
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
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
            FileReader(cf.toFile()).use { reader -> this.conf.read(reader) }
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

fun repoDefaultConfig(): INIConfiguration {
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

fun repoFind(path: Path = Paths.get("."), required: Boolean = true): GitRepository? {
    val path = path.absolute()

    if (path.resolve(".git").isDirectory()) {
        return GitRepository(path)
    }

    val parent = path.parent

    if (parent == null) {
        check(required) { "No git directory." }

        return null
    }

    return repoFind(parent, required)
}

abstract class GitObject(data: ByteArray? = null) {
    abstract val fmt: ByteArray

    init {
        data?.let { deserialize(it) } ?: init()
    }

    abstract fun serialize(): ByteArray

    abstract fun deserialize(data: ByteArray): Unit

    open fun init(): Unit {

    }
}

fun objectRead(repo: GitRepository, sha: String): GitObject? {
    val path = repoFile(
        repo, Paths.get("objects"),
        Paths.get(sha.substring(0, 2)), Paths.get(sha.substring(2))
    )

    if (path == null || !path.isReadable()) return null

    val bytesToDecompress = File(path.toString()).readBytes()
    val raw: ByteArray = ByteArrayOutputStream().use { bos ->
        InflaterInputStream(ByteArrayInputStream(bytesToDecompress)).use { iip ->
            iip.copyTo(bos)
        }
        bos.toByteArray()
    }

    with(Inflater()) {
        setInput(bytesToDecompress)
        inflate(raw)
        end()
    }

    val fmtIndex = raw.indexOf(' '.code.toByte())
    val fmt = raw.sliceArray(0..<fmtIndex)

    val sizeIndex = raw.sliceArray(fmtIndex..<raw.size)
        .indexOf(0x00.toByte()) + fmtIndex
    val size = String(raw, fmtIndex + 1, sizeIndex - fmtIndex - 1, Charsets.US_ASCII).toInt()

    require(size == raw.size - sizeIndex - 1) { "Malformed object $sha: bad length" }

    val gitObject = when (fmt.decodeToString()) {
        "commit" -> GitCommit
        "tree" -> GitTree
        "tag" -> GitTag
        "blob" -> GitBlob
        else -> throw IOException("Unknow type ${fmt.decodeToString()} for object $sha}")
    }

    return gitObject.create(raw.sliceArray(sizeIndex + 1..<raw.size))
}

fun objectWrite(obj: GitObject, repo: GitRepository? = null): String {
    val data = obj.serialize()

    val result = obj.fmt + " ${data.size}\u0000".toByteArray(Charsets.US_ASCII) + data

    val shaInBytes = MessageDigest.getInstance("SHA-1").digest(result)
    val formatter = Formatter()

    for (b in shaInBytes) {
        formatter.format("%02x", b)
    }
    val sha = formatter.toString()

    if (repo != null) {
        val path = repoFile(
            repo, Paths.get("objects"),
            Paths.get(
                sha.substring(0..1)
            ),
            Paths.get(sha.substring(2..<sha.length)), mkdir = true
        )

        if (path != null && !path.exists()) {
            val objectFile = File(path.toString())
            ByteArrayOutputStream().use { bos ->
                DeflaterOutputStream(bos).use { dos ->
                    dos.write(result)
                    dos.finish()
                    objectFile.writeBytes(bos.toByteArray())
                }
            }
        }
    }
    return sha

}

abstract class GitObjectFactory {

    abstract fun create(data: ByteArray? = null): GitObject
}

class GitCommit(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitCommit(data)
    }

    override val fmt = "commit".toByteArray()

    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deserialize(data: ByteArray) {
        TODO("Not yet implemented")
    }

}

class GitTree(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitTree(data)
    }

    override val fmt = "tree".toByteArray()

    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deserialize(data: ByteArray) {
        TODO("Not yet implemented")
    }
}

class GitTag(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitTag(data)
    }

    override val fmt = "tag".toByteArray()

    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deserialize(data: ByteArray) {
        TODO("Not yet implemented")
    }
}

class GitBlob(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitBlob(data)
    }

    override val fmt = "blob".toByteArray()

    lateinit var blobData: ByteArray

    override fun serialize(): ByteArray {
        return blobData
    }

    override fun deserialize(data: ByteArray) {
        blobData = data
    }
}

fun catFile(repo: GitRepository, obj: String, fmt: ByteArray? = null) {
    val obj = objectRead(repo, objectFind(repo, obj, fmt))
    if (obj != null) System.out.writeBytes(obj.serialize())
}

fun objectFind(
    repo: GitRepository,
    name: String,
    fmt: ByteArray? = null,
    follow: Boolean = true
): String {
    return name
}


class MGit : CliktCommand() {
    override fun run() = Unit
}

class Init : CliktCommand(name = "init") {
    val path: String by argument().default("./")
    override fun help(context: Context) =
        "Create an empty Git repository or reinitialize an existing one"

    override fun run() {
        repoCreate(Paths.get(path))
    }
}

class CatFile : CliktCommand(name = "cat-file") {
    val type: String by argument(help = "Specify the type")
        .choice("blob", "commit", "tag", "tree")
    val objectName: String by argument(name = "object", help = "The object to display")

    override fun help(context: Context) =
        "Provide content of repository objects"

    override fun run() {
        val repo = repoFind()
        if (repo != null) catFile(repo, objectName, type.toByteArray())

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
    }
}


fun main(args: Array<String>) = MGit()
    .subcommands(Init())
    .subcommands(CatFile())
    .subcommands(HashObject())
    .main(args)