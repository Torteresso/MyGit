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
import org.apache.commons.configuration2.INIConfiguration
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.Formatter
import java.util.concurrent.TimeUnit
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.String
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.math.ceil
import kotlin.time.Instant


// IDEA TODO :
/*
* Write and interface with every function so I can implement a functional version vs imperative and compare them (performance, etc...)
* Write some tests for important functions
* Refactor into multiple files
* For the android app, write an interface so that I can use either mGit or the real git. So I can use some command of git that I didn't implement
* */

data class GitRepository(val worktree: Path, val force: Boolean = false) {
    val gitdir: Path = worktree.resolve(".git")
    var conf: INIConfiguration = INIConfiguration()

    init {

        require((force || this.gitdir.isDirectory())) { "$gitdir is not a Git repository" }

        val cf: Path? = repoFile(this, Paths.get("config"))

        if (cf != null && cf.isReadable()) {
            FileReader(cf.toFile()).use { reader -> this.conf.read(reader) }
        } else if (!force) {
            throw IOException("Configuration file missing")
        }

        if (!force) {
            val version = this.conf.getProperty("core.repositoryformatversion")
            require(version == "0") { "Unsupported repositoryformatversion: $version" }
        }


    }
}

fun repoPath(repo: GitRepository, vararg path: Path): Path {
    return path.fold(repo.gitdir) {acc, path -> acc.resolve(path)}
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
    val coreSection = config.getSection("core")  // create the section if she doesn't exist
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

fun repoFind(path: Path = Paths.get(System.getProperty("user.dir")), required: Boolean = true): GitRepository? {

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
        deserialize(data ?: ByteArray(0))
    }

    abstract fun serialize(): ByteArray

    abstract fun deserialize(data: ByteArray)

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

    val sizeIndex = raw
        .indexOf(0x00, fmtIndex)
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
    lateinit var kvlm: MutableMap<String?, MutableList<ByteArray>>

    override fun serialize(): ByteArray {
        return kvlmSerialize(kvlm)
    }

    override fun deserialize(data: ByteArray) {
        kvlm = kvlmParse(data)
    }

}

class GitTree(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitTree(data)
    }

    override val fmt = "tree".toByteArray()
    lateinit var items: MutableList<GitTreeLeaf>

    override fun serialize(): ByteArray {
        return treeSerialize(this)
    }

    override fun deserialize(data: ByteArray) {
        items = treeParse(data)
    }
}

class GitTag(data: ByteArray? = null) : GitObject(data) {

    companion object Factory : GitObjectFactory() {
        override fun create(data: ByteArray?) = GitTag(data)
    }

    override val fmt = "tag".toByteArray()
    lateinit var kvlm: MutableMap<String?, MutableList<ByteArray>>

    override fun serialize(): ByteArray {
        return kvlmSerialize(kvlm)
    }

    override fun deserialize(data: ByteArray) {
        kvlm = kvlmParse(data)
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
    val sha = objectFind(repo, obj, fmt)
    require(sha != null) { "Could not find sha associated with obj $obj" }
    val obj = objectRead(repo, sha)
    if (obj != null) System.out.writeBytes(obj.serialize())
}

fun objectFind(
    repo: GitRepository,
    name: String,
    fmt: ByteArray? = null,
    follow: Boolean = true
): String? {
    val shaAsList = objectResolve(repo, name)

    require(shaAsList != null) { "No such reference $name." }
    require(shaAsList.size == 1) {
        "Ambiguous reference $name: Candidates are\n - ${
            shaAsList.joinToString(
                "\n - "
            )
        }."
    }

    var sha = shaAsList[0]


    if (fmt == null) return sha

    while (true) {
        if (name == "HEAD" && sha == null) throw IOException("In mgit, you must have at least one commit to check status.")
        require(sha != null) { "Sha should not be null for reference $name." }
        val obj = objectRead(repo, sha)
        require(obj != null){"Could not read object with sha : $sha"}

        if (obj.fmt.contentEquals(fmt)) return sha
        if (!follow) return null

        sha = if (obj.fmt.contentEquals("tag".toByteArray())) {
            (obj as GitTag).kvlm["object"]!!.single().decodeToString()
        } else if (obj.fmt.contentEquals("commit".toByteArray())
            && fmt.contentEquals("tree".toByteArray())
        ) {
            (obj as GitCommit).kvlm["tree"]!!.single().decodeToString()
        } else {
            return null
        }
    }
}

fun objectHash(fd: File, fmt: ByteArray, repo: GitRepository? = null): String {
    val data = fd.readBytes()

    val gitObject = when (fmt.decodeToString()) {
        "commit" -> GitCommit(data)
        "tree" -> GitTree(data)
        "tag" -> GitTag(data)
        "blob" -> GitBlob(data)
        else -> throw IOException("Unknow type ${fmt.decodeToString()} !")
    }
    return objectWrite(gitObject, repo)
}

fun ByteArray.indexOf(byte: Byte, startIndex: Int): Int {
    for (i in startIndex until size) {
        if (this[i] == byte) return i
    }
    return -1
}

fun kvlmParse(
    raw: ByteArray, start: Int = 0,
    dct: MutableMap<String?, MutableList<ByteArray>> = mutableMapOf()
)
        : MutableMap<String?, MutableList<ByteArray>> {

    if (raw.isEmpty()) return mutableMapOf()

    val spaceIndex = raw.indexOf(' '.code.toByte(), start)
    val newLineIndex = raw.indexOf('\n'.code.toByte(), start)

    if (spaceIndex !in 0..newLineIndex) {
        require(newLineIndex == start) { "Malformed commit : $raw" }
        dct[null] = mutableListOf(raw.sliceArray(start + 1..<raw.size))
        return dct
    }

    val key = raw.sliceArray(start..<spaceIndex)
    var end = start

    while (true) {
        end = raw.indexOf('\n'.code.toByte(), end + 1)
        if (raw.elementAt(end + 1) != ' '.code.toByte()) break
    }

    val value = raw.sliceArray((spaceIndex + 1)..<end).toString(Charsets.US_ASCII)
        .replace("\n ", "\n")
        .toByteArray(Charsets.US_ASCII)

    if (key.decodeToString() in dct) {
        dct[key.decodeToString()]!!.add(value)
    } else {
        dct[key.decodeToString()] = mutableListOf(value)
    }

    return kvlmParse(raw, start = end + 1, dct = dct)
}

fun kvlmSerialize(kvlm: MutableMap<String?, MutableList<ByteArray>>): ByteArray {

    var ret = ByteArray(0)
    if (kvlm.keys.isEmpty()) return ret

    for (k in kvlm.keys) {
        if (k == null) continue
        val value = kvlm[k]

        if (value != null) {
            for (v in value) {
                ret += k.toByteArray() + ' '.code.toByte() + v.toString(Charsets.US_ASCII)
                    .replace("\n", "\n ")
                    .toByteArray(Charsets.US_ASCII) + '\n'.code.toByte()
            }
        }
    }

    ret += "\n".toByteArray() + kvlm[null]!!.single()

    return ret
}

fun logGraphviz(repo: GitRepository, sha: String, seen: MutableSet<String>) {
    if (sha in seen) return

    seen.add(sha)

    val commit = objectRead(repo, sha) as? GitCommit
        ?: throw IOException("Sha should point to a commit Object in log command.")

    var message = commit.kvlm[null]!!.single().decodeToString().trim()
    message.replace("\\", "\\\\")
    message.replace("\"", "\\\"")


    if ("\n" in message) {
        message = message.substring(0..<message.indexOf("\n"))
    }

    println("  c_$sha [label=\"${sha.substring(0..6)}: $message\"]")

    if ("parent" !in commit.kvlm.keys)
        return

    val parents = commit.kvlm.getValue("parent")

    for (p in parents) {
        val pDecoded = p.toString(Charsets.US_ASCII)
        println("  c_$sha -> c_$pDecoded;")
        logGraphviz(repo, pDecoded, seen)
    }
}

data class GitTreeLeaf(val mode: ByteArray, val path: String, val sha: String)

fun treeParseOne(raw: ByteArray, start: Int = 0): Pair<Int, GitTreeLeaf> {
    val modeTerminatorIndex = raw.indexOf(' '.code.toByte(), start)

    require(modeTerminatorIndex - start == 5 || modeTerminatorIndex - start == 6)
    { "Wrong position for mode terminator of the tree $raw" }

    var mode = raw.sliceArray(start..<modeTerminatorIndex)
    if (mode.size == 5) mode = "0".toByteArray() + mode

    val pathTerminatorIndex = raw
        .indexOf(0x00.toByte(), modeTerminatorIndex)

    val path = raw.sliceArray(modeTerminatorIndex + 1..<pathTerminatorIndex)

    val sha = raw.sliceArray(pathTerminatorIndex + 1..<pathTerminatorIndex + 21).toHexString()

    return Pair(pathTerminatorIndex + 21, GitTreeLeaf(mode, path.decodeToString(), sha))
}

fun treeParse(raw: ByteArray): MutableList<GitTreeLeaf> {
    var pos = 0
    val max = raw.size
    val ret = mutableListOf<GitTreeLeaf>()
    while (pos < max) {
        val (newPos, data) = treeParseOne(raw, pos)
        pos = newPos
        ret.add(data)
    }
    return ret
}

fun treeSortComparator(leaf1: GitTreeLeaf, leaf2: GitTreeLeaf): Int {
    val leafPath1 = if (leaf1.mode.first() == "4".toByte()) leaf1.path + "/" else leaf1.path
    val leafPath2 = if (leaf2.mode.first() == "4".toByte()) leaf2.path + "/" else leaf2.path

    return compareValues(leafPath1, leafPath2)
}

fun treeSerialize(obj: GitTree): ByteArray {
    obj.items.sortWith { leaf, leaf1 -> treeSortComparator(leaf, leaf1) }

    var ret = ByteArray(0)

    for (leaf in obj.items) {
        ret += leaf.mode
        ret += ' '.code.toByte()
        ret += leaf.path.encodeToByteArray()
        ret += byteArrayOf(0)
        ret += leaf.sha.hexToByteArray()
    }

    return ret
}

fun lsTree(repo: GitRepository, ref: String, recursive: Boolean?, prefix: String = "") {
    val sha = objectFind(repo, ref, "tree".toByteArray())
    require(sha != null) { "Could not find sha associated with name $ref" }
    val obj = objectRead(repo, sha) as? GitTree
        ?: throw IllegalArgumentException("No valid object named $ref")

    for (item in obj.items) {
        val type =
            if (item.mode.size == 5) item.mode.sliceArray(0..<1)
           else item.mode.sliceArray(0..<2)

        val typeName = when (type.decodeToString()) {
            "04" -> "tree"
            "10" -> "blob"
            "12" -> "blob"
            "16" -> "commit"

            else -> throw IllegalArgumentException("Weird tree leaf mode : ${item.mode}")
        }

        if (recursive == null || !recursive || (typeName != "tree")) {
            println(
                "${"0".repeat(6 - item.mode.size) + item.mode.decodeToString()} $typeName ${item.sha}    ${
                    Paths.get(
                        prefix
                    ).resolve(item.path)
                }"
            )
        } else {
            lsTree(
                repo, item.sha, recursive,
                Paths
                    .get(prefix)
                    .resolve(item.path).toString()
            )
        }
    }
}

fun treeCheckout(repo: GitRepository, tree: GitTree, path: Path) {
    for (item in tree.items) {
        val obj = objectRead(repo, item.sha)
        val dest = path.resolve(item.path)

        require(obj != null) { "No valid object for sha ${item.sha}" }

        if (obj.fmt.contentEquals("tree".toByteArray())) {
            dest.createDirectory()
            treeCheckout(repo, obj as GitTree, dest)
        } else if (obj.fmt.contentEquals("blob".toByteArray())) {
            File(dest.toString()).writeBytes((obj as GitBlob).blobData)
        }
    }
}

fun refResolve(repo: GitRepository, ref: Path): String? {
    val path = repoFile(repo, ref)

    if (path == null || !path.isReadable()) return null

    val data = File(path.toString()).readText().dropLast(1)

    if (data.startsWith("ref: ")) {
        return refResolve(repo, Paths.get(data.substring(5..<data.length)))
    }
    return data
}

fun refList(repo: GitRepository, path1: Path? = null): MutableMap<Path, Any?> {
    val path = path1 ?: repoDir(repo, Paths.get("refs"))

    require(path != null) { "Refs directory not found." }

    val allRefs = mutableMapOf<Path, Any?>()


    for (f in path.listDirectoryEntries().sorted()) {
        val f = path.relativize(f)
        val newPath = path.resolve(f)
        if (newPath.isDirectory()) {
            allRefs[f] = refList(repo, newPath)
        } else {
            allRefs[f] = refResolve(repo, newPath)
        }
    }

    return allRefs
}

fun showRef(
    repo: GitRepository,
    refs: MutableMap<Path, Any?>,
    withHash: Boolean = true,
    prefix: String = ""
) {
    val prefix = if (prefix.isNotEmpty()) "$prefix/" else prefix

    for ((k, v) in refs.entries) {
        when (v) {
            is String if withHash -> {
                println("$v $prefix$k")
            }

            is String -> {
                println("$prefix$k")
            }

            else -> {
                @Suppress("UNCHECKED_CAST")
                showRef(repo, v as MutableMap<Path, Any?>, withHash, "$prefix$k")
            }
        }
    }

}

fun tagCreate(repo: GitRepository, name: String, ref: String, createTagObject: Boolean = false) {
    val sha = objectFind(repo, ref)
    require(sha != null) { "Could not find sha associated with name $ref" }

    if (createTagObject) {
        val tag = GitTag()
        tag.kvlm["object"] = mutableListOf(sha.encodeToByteArray())
        tag.kvlm["type"] = mutableListOf("commit".toByteArray())
        tag.kvlm["tag"] = mutableListOf(name.encodeToByteArray())
        tag.kvlm["tagger"] = mutableListOf("mgit <mgit@example.com>".toByteArray())
        tag.kvlm[null] =
            mutableListOf("A tag generated by mgit, which won't let you customize the message:\n".toByteArray())

        val tagSha = objectWrite(tag, repo)

        refCreate(repo, "tags/$name", tagSha)
    } else {
        refCreate(repo, "tags/$name", sha)
    }
}

fun refCreate(repo: GitRepository, refName: String, sha: String) {
    val path = repoFile(repo, Paths.get("refs/$refName"))
    require(path != null) { "Could not create a correct path to refs/$refName" }
    File(path.toString()).writeText(sha + "\n")
}

fun objectResolve(repo: GitRepository, name: String): MutableList<String?>? {
    val candidates = mutableListOf<String?>()
    val hashRE = "^[0-9A-Fa-f]{4,40}$".toRegex()

    if (name.trim().isEmpty())
        return null

    if (name == "HEAD") {
        return mutableListOf(refResolve(repo, Paths.get("HEAD")))
    }

    if (hashRE.matches(name)) {
        val name = name.lowercase()
        val prefix = name.substring(0..<2)
        val path = repoDir(repo, Paths.get("objects"), Paths.get(prefix), mkdir = false)
        if (path != null) {
            val rem = name.substring(2..<name.length)
            for (f in path.listDirectoryEntries()) {
                val f = path.relativize(f)
                if (f.startsWith(rem)) {
                    candidates.add(prefix + f)
                }
            }
        }

    }

    val asTag = refResolve(repo, Paths.get("refs/tags/$name"))
    if (asTag != null) {
        candidates.add(asTag)
    }
    val asBranch = refResolve(repo, Paths.get("refs/heads/$name"))
    if (asBranch != null) {
        candidates.add(asBranch)
    }
    val asRemoteBranch = refResolve(repo, Paths.get("refs/remotes/$name"))
    if (asRemoteBranch != null) {
        candidates.add(asRemoteBranch)
    }

    return candidates
}

data class GitIndexEnTry(
    val cTime: Pair<Int, Int>,
    val mTime: Pair<Int, Int>,
    val dev: Int,
    val ino: Int,
    val modeType: Int,
    val modePerms: Int,
    val uid: Int,
    val gid: Int,
    val fSize: Int,
    val sha: String,
    val flagAssumeValid: Boolean,
    val flagStage: Int,
    val name: String
)

data class GitIndex(
    val version: Int = 2,
    var entries: MutableList<GitIndexEnTry> = mutableListOf()
)

fun indexRead(repo: GitRepository): GitIndex {
    val indexFile = repoFile(repo, Paths.get("index"))

    if (indexFile == null || !indexFile.isReadable()) return GitIndex()

    val raw = File(indexFile.toString()).readBytes()

    val header = raw.sliceArray(0..<12)
    val signature = header.sliceArray(0..<4)
    require(signature.contentEquals("DIRC".toByteArray())) { "The signature is not DIRC." }
    val version = ByteBuffer.wrap(header, 4, 4).int
    require(version == 2) { "mgit only supports index file version 2" }
    val count = ByteBuffer.wrap(header, 8, 4).int

    val entries = mutableListOf<GitIndexEnTry>()

    val content = raw.sliceArray(12..<raw.size)
    var idx = 0
    repeat(count) {
        val cTimeS = ByteBuffer.wrap(content, idx, 4).int
        val cTimeNs = ByteBuffer.wrap(content, idx + 4, 4).int
        val mTimeS = ByteBuffer.wrap(content, idx + 8, 4).int
        val mTimeNs = ByteBuffer.wrap(content, idx + 12, 4).int
        val dev = ByteBuffer.wrap(content, idx + 16, 4).int
        val ino = ByteBuffer.wrap(content, idx + 20, 4).int
        val unused = ByteBuffer.wrap(content, idx + 24, 2).short.toInt().and(0xFFFF)
        val mode = ByteBuffer.wrap(content, idx + 26, 2).short.toInt().and(0xFFFF)
        val uid = ByteBuffer.wrap(content, idx + 28, 4).int
        val gid = ByteBuffer.wrap(content, idx + 32, 4).int
        val fSize = ByteBuffer.wrap(content, idx + 36, 4).int
        val sha = content.sliceArray(idx+40..<idx+60).toHexString()
        val flags = ByteBuffer.wrap(content, idx + 60, 2).short.toInt().and(0xFFFF)

        require(unused == 0) { "Unused variable should be 0" }
        val modeType = mode.shr(12)
        require(modeType in listOf(0b1000, 0b1010, 0b1110)) { "Malformed modetype $modeType" }
        val modePerms = mode.and(0b0000000111111111)
        val flagAssumeValid = flags.and(0b1000000000000000) != 0
        val flagExtended = flags.and(0b01000000000000000) != 0
        require(!flagExtended) { "Extended flag should be false" }
        val flagStage = flags.and(0b00110000000000000)
        val nameLength = flags.and(0b0000111111111111)

        idx += 62

        val rawName: ByteArray
        if (nameLength < 0xFFF) {
            require(content[idx + nameLength] == 0x00.toByte()) { "We didn't reach the end of the section successfully" }
            rawName = content.sliceArray(idx..<idx + nameLength)
            idx += nameLength + 1
        } else {
            println("Notice: Name is 0x$nameLength bytes long.")
            //        /!\ PROBABLY BROKEN /!\
            val nullIdx = content.indexOf(0x00.toByte(), idx + 0xFFF)
            rawName = content.sliceArray(idx..<nullIdx)
            idx = nullIdx + 1
        }

        val name = rawName.decodeToString()

        idx = 8 * ceil(idx.toDouble() / 8).toInt()

        entries.add(
            GitIndexEnTry(
                cTime = Pair(cTimeS, cTimeNs),
                mTime = Pair(mTimeS, mTimeNs),
                dev = dev,
                ino = ino,
                modeType = modeType,
                modePerms = modePerms,
                uid = uid,
                gid = gid,
                fSize = fSize,
                sha = sha,
                flagAssumeValid = flagAssumeValid,
                flagStage = flagStage,
                name = name
            )
        )
    }

    return GitIndex(version = version, entries = entries)
}

fun gitignoreParsel(raw: String): Pair<String, Boolean>? {
    val raw = raw.trim()
    return if (raw.isEmpty() || raw.elementAt(0) == '#') {
        null
    } else if (raw.elementAt(0) == '!') {
        Pair(raw.substring(1..<raw.length), false)
    } else if (raw.elementAt(0) == '\\') {
        Pair(raw.substring(1..<raw.length), true)
    } else {
        Pair(raw, true)
    }
}

fun gitignoreParse(lines: List<String>): MutableList<Pair<String, Boolean>?> {
    val ret = mutableListOf<Pair<String, Boolean>?>()

    for (line in lines) {
        val parsed = gitignoreParsel(line)
        if (parsed != null) ret.add(parsed)
    }

    return ret
}

data class GitIgnore(
    val absolute: List<MutableList<Pair<String, Boolean>?>>,
    val scoped: Map<String, MutableList<Pair<String, Boolean>?>>
)

fun gitignoreRead(repo: GitRepository): GitIgnore {
    val absolute = mutableListOf<MutableList<Pair<String, Boolean>?>>()
    val scoped = mutableMapOf<String, MutableList<Pair<String, Boolean>?>>()

    val repoFile = repo.gitdir.resolve("info/exclude")
    if (repoFile.isReadable()) {
        absolute.add(gitignoreParse(File(repoFile.toString()).readLines()))
    }

    val configHome =
        System.getenv("XDG_CONFIG_HOME") ?: "${System.getProperty("user.home")}/.config"
    val globalFile = Paths.get(configHome).resolve("git/ignore")

    if (globalFile.isReadable()) {
        absolute.add(gitignoreParse(File(globalFile.toString()).readLines()))
    }

    val index = indexRead(repo)

    for (entry in index.entries) {
        if (entry.name == ".gitignore" || entry.name.endsWith("/.gitignore")) {
            val dirName = entry.name.substringBeforeLast('/', missingDelimiterValue = "")
            val contents = objectRead(repo, entry.sha)
            val lines = (contents as GitBlob).blobData.decodeToString().lines()
            scoped[dirName] = gitignoreParse(lines)

        }
    }

    return GitIgnore(absolute.toList(), scoped.toMap())
}

fun checkIgnore1(rules: MutableList<Pair<String, Boolean>?>, path: String): Boolean? {
    var result: Boolean? = null

    for ((pattern, value) in rules.filterNotNull()) {
        if (path.matches(pattern.toRegex())) result = value
    }

    return result
}

fun checkIgnoreScoped(
    rules: Map<String, MutableList<Pair<String, Boolean>?>>,
    path: String
): Boolean? {
    var parent = path.substringBeforeLast('/', missingDelimiterValue ="")

    while (true) {
        if (parent in rules) {
            val result = checkIgnore1(rules.getValue(parent), path)
            if (result != null) return result
        }
        if (parent.isEmpty()) break

        parent = parent.substringBeforeLast('/',  missingDelimiterValue ="")
    }

    return null
}

fun checkIgnoreAbsolute(rules: List<MutableList<Pair<String, Boolean>?>>, path: String): Boolean {
    for (ruleset in rules) {
        val result = checkIgnore1(ruleset, path)
        if (result != null) return result
    }

    return false
}

fun checkIgnore(rules: GitIgnore, path: String): Boolean {
    require(!Paths.get(path).isAbsolute) { "The function checkIgnore requires path to be relative to the repository's root" }

    val result = checkIgnoreScoped(rules.scoped, path)
    if (result != null) return result

    return checkIgnoreAbsolute(rules.absolute, path)
}

fun getActiveBranch(repo: GitRepository): String? {
    val headFile =
        repoFile(repo, Paths.get("HEAD")) ?: throw IOException("Could not find HEAD file.")

    val head = File(headFile.toString()).readText()

    return if (head.startsWith("ref: refs/heads/")) {
        head.substring(16..<head.length -1)
    } else null
}

fun showStatusBranch(repo: GitRepository) {
    val branch = getActiveBranch(repo)

    if (branch != null) {
        println("On branch $branch.")
    } else {
        val head = objectFind(repo, "HEAD") ?: throw IOException("Could not find HEAD file.")
        println("HEAD detached at $head")
    }
}

fun convertTreeToDict(
    repo: GitRepository,
    ref: String,
    prefix: String = ""
): MutableMap<String, String> {
    val ret = mutableMapOf<String, String>()

    val treeSha = objectFind(repo, ref, fmt = "tree".toByteArray())
        ?: throw IOException("Could not find $ref object.")
    val tree = objectRead(repo, treeSha) ?: throw IOException("Could not read object $treeSha.")

    for (leaf in (tree as GitTree).items) {
        val fullPath = Paths.get(prefix).resolve(leaf.path)
        val isSubtree = leaf.mode.sliceArray(0..1).contentEquals("04".toByteArray())
        if (isSubtree) {
            ret.putAll(convertTreeToDict(repo, leaf.sha, fullPath.toString()))
        } else {
            ret[fullPath.toString()] = leaf.sha
        }
    }
    return ret
}

fun showStatusHeadIndex(repo: GitRepository, index: GitIndex) {
    println("Changes to be committed:")

    val head = convertTreeToDict(repo, "HEAD")
    for (entry in index.entries) {
        if (entry.name in head.keys) {
            if (head[entry.name] != entry.sha) println("  modified:    ${entry.name}")
            head.remove(entry.name)
        } else println("  added:    ${entry.name}")
    }
    for (entry in head.keys) {
        println("  deleted:    $entry")
    }
}

fun showStatusIndexWorktree(repo: GitRepository, index: GitIndex) {
    println("Changes not staged for commit:")

    val ignore = gitignoreRead(repo)

    val allFiles = mutableListOf<String>()

    repo.worktree.walk().forEach { path ->
        if (!(path.startsWith(repo.gitdir))) {
            allFiles.add(repo.worktree.relativize(path).toString())
        }
    }

    for (entry in index.entries) {
        val fullPath = repo.worktree.resolve(entry.name)
        if (!fullPath.isReadable()) println("  deleted:    ${entry.name}")
        else {
            val cTimeNs = (entry.cTime.first * 1_000_000_000L + entry.cTime.second)
            val mTimeNs = (entry.mTime.first * 1_000_000_000L + entry.mTime.second)

            val fileCTimeNs =
                (Files.getAttribute(fullPath, "unix:ctime") as FileTime).to(TimeUnit.NANOSECONDS)
            val fileMTimeNs =
                (Files.getAttribute(fullPath, "unix:lastModifiedTime") as FileTime).to(TimeUnit.NANOSECONDS)

            if (cTimeNs != fileCTimeNs || mTimeNs != fileMTimeNs) {
                val newSha = objectHash(File(fullPath.toString()), "blob".toByteArray(), null)

                if (entry.sha != newSha) println("  modified:    ${entry.name}")
            }
        }

        if (entry.name in allFiles) allFiles.remove(entry.name)
    }

    println()
    println("Untracked files:")

    for (f in allFiles) {
        if (!checkIgnore(ignore, f)) println("    $f")
    }

}

fun Int.to4Bytes(): ByteArray {
    return ByteBuffer.allocate(4).putInt(this).array()
}

fun Int.to2Bytes(): ByteArray {
    require(this in 0..0xFFFF) { "Value $this does not fit in 2 bytes unsigned" }
    return ByteBuffer.allocate(2).putShort(this.toShort()).array()
}

fun indexWrite(repo: GitRepository, index: GitIndex) {
    val indexPath =
        repoFile(repo, Paths.get("index")) ?: throw IOException("Could not find/create index file.")

    val f = File(indexPath.toString())

    f.writeBytes("DIRC".toByteArray())
    f.appendBytes(index.version.to4Bytes())
    f.appendBytes(index.entries.size.to4Bytes())

    var idx = 0

    for (e in index.entries) {
        f.appendBytes(e.cTime.first.to4Bytes())
        f.appendBytes(e.cTime.second.to4Bytes())
        f.appendBytes(e.mTime.first.to4Bytes())
        f.appendBytes(e.mTime.second.to4Bytes())
        f.appendBytes(e.dev.to4Bytes())
        f.appendBytes(e.ino.to4Bytes())

        val mode = e.modeType.shl(12).or(e.modePerms)
        f.appendBytes(mode.to4Bytes())
        f.appendBytes(e.uid.to4Bytes())
        f.appendBytes(e.gid.to4Bytes())
        f.appendBytes(e.fSize.to4Bytes())
        f.appendBytes(e.sha.hexToByteArray())
        val flagAssumeValid = if (e.flagAssumeValid) 0x1.shl(15) else 0
        val nameBytes = e.name.encodeToByteArray()
        val nameLength = if (nameBytes.size >= 0xFFF) 0xFFF else nameBytes.size

        f.appendBytes((flagAssumeValid.or(e.flagStage).or(nameLength)).to2Bytes())
        f.appendBytes(nameBytes)
        f.appendBytes(byteArrayOf(0))

        idx += 62 + nameBytes.size + 1

        if (idx % 8 != 0) {
            val pad = 8 - (idx % 8)
            f.appendBytes(ByteArray(pad))
            idx += pad
        }
    }
}

fun rm(
    repo: GitRepository,
    paths: List<String>,
    delete: Boolean = true,
    skipMissing: Boolean = false
) {
    val index = indexRead(repo)

    val absPaths = mutableSetOf<String>()

    for (path in paths) {
        val absPath = Paths.get(path).absolute()
        require(absPath.startsWith(repo.worktree)) { "Cannot remove paths outside of worktree: $path" }
        absPaths.add(absPath.toString())
    }

    val keptEntries = mutableListOf<GitIndexEnTry>()
    val remove = mutableListOf<String>()

    for (e in index.entries) {
        val fullPath = repo.worktree.resolve(e.name).toString()

        if (fullPath in absPaths) {
            remove.add(fullPath)
            absPaths.remove(fullPath)
        } else keptEntries.add(e)
    }

    require(absPaths.isEmpty() || skipMissing) { "Cannot remove paths not in the index: $absPaths" }

    if (delete) {
        for (path in remove) {
            print("Are you sure you want to delete $path ? (y/n)  ")
            while(true) {
                val userResponse = readln()
                    if (userResponse == "y") {
                        Paths.get(path).deleteIfExists()
                        break
                    }
                    else if (userResponse == "n")
                    {
                        println("Cancelled removal of all items in $remove")
                        return
                    }
                    else print("Type 'y' for yes and 'n' for no.  ")
                }
            }
        }


    index.entries = keptEntries
    indexWrite(repo, index)
}

fun add(
    repo: GitRepository,
    paths: List<String>
) {
    rm(repo, paths, delete = false, skipMissing = true)

    val cleanPaths = mutableSetOf<Pair<String, String>>()
    for (path in paths) {
        val absPath = Paths.get(path).absolute()
        require(absPath.startsWith(repo.worktree) && absPath.isReadable()) { "Not a file, or outside the worktree: $paths" }

        val relPath = repo.worktree.relativize(absPath)
        cleanPaths.add(Pair(absPath.toString(), relPath.toString()))
    }

    val index = indexRead(repo)

    for ((absPath, relPath) in cleanPaths) {
        val fd = File(absPath)
        val sha = objectHash(fd, "blob".toByteArray(), repo)

        val cTimeS =
            (Files.getAttribute(Paths.get(absPath), "unix:ctime") as FileTime).to(TimeUnit.SECONDS)
        val cTimeNs = (Files.getAttribute(
            Paths.get(absPath),
            "unix:ctime"
        ) as FileTime).to(TimeUnit.NANOSECONDS) % 1_000_000_000L
        val mTimeS =
            (Files.getAttribute(Paths.get(absPath), "unix:lastModifiedTime") as FileTime).to(TimeUnit.SECONDS)
        val mTimeNs = (Files.getAttribute(
            Paths.get(absPath),
            "unix:lastModifiedTime"
        ) as FileTime).to(TimeUnit.NANOSECONDS) % 1_000_000_000L

        val dev = (Files.getAttribute(Paths.get(absPath), "unix:dev") as Long).toInt()
        val ino = (Files.getAttribute(Paths.get(absPath), "unix:ino") as Long).toInt()
        val uid = (Files.getAttribute(Paths.get(absPath), "unix:uid") as Int)
        val gid = (Files.getAttribute(Paths.get(absPath), "unix:gid") as Int)
        val fSize = (Files.getAttribute(Paths.get(absPath), "unix:size") as Long).toInt()

        val entry = GitIndexEnTry(
            cTime = Pair(cTimeS.toInt(), cTimeNs.toInt()),
            mTime = Pair(mTimeS.toInt(), mTimeNs.toInt()),
            dev = dev,
            ino = ino,
            modeType = 0b1000,
            modePerms = 0b110100100, // 0o644 in binary since Kotlin don't support octal
            uid = uid,
            gid = gid,
            fSize = fSize,
            sha = sha,
            flagAssumeValid = false,
            flagStage = 0,
            name = relPath
        )

        index.entries.add(entry)
    }

    indexWrite(repo, index)
}

fun gitConfigRead(): INIConfiguration? {
    val userExpansion = System.getProperty("user.home")
    val xdgConfigHome =
        System.getenv("XDG_CONFIG_HOME") ?: "$userExpansion"
    val configFiles = listOf(
        Paths.get(xdgConfigHome).resolve(".gitconfig").toString()
            .replaceFirst("~", userExpansion),
        "$userExpansion/.gitconfig"
    )

    val config = INIConfiguration()
    for (file in configFiles) {
        if (!Paths.get(file).isReadable()) continue
        FileReader(file).use { reader ->
            config.read(reader)
        }
    }

    return if (config.isEmpty) null else config
}

fun getUserGitConfig(config: INIConfiguration?): String? {
    if (config == null) return null
    val userSection = config.getSection("user")

    return if (userSection.containsKey("name") && userSection.containsKey("email")) {
        "${userSection.getString("name")} <${userSection.getString("email")}>"
    } else null
}

fun treeFromIndex(repo: GitRepository, index: GitIndex): String? {
    val contents = mutableMapOf<String, MutableList<Any>>()
    contents[""] = mutableListOf()

    for (entry in index.entries) {
        val dirName = entry.name.substringBeforeLast("/", missingDelimiterValue = "")

        var key = dirName
        while (key != "") {
            if (key !in contents) contents[key] = mutableListOf()

            key = key.substringBeforeLast("/", missingDelimiterValue = "")
        }

        contents.getValue(dirName).add(entry)
    }

    val sortedPaths = contents.keys.sortedByDescending { it.length }

    var sha: String? = null

    for (path in sortedPaths) {
        val tree = GitTree()

        var leaf: GitTreeLeaf
        for (entry in contents.getValue(path)) {
            when (entry) {
                is GitIndexEnTry -> {
                    val modeString = entry.modeType.toString(8).padStart(2, '0') +
                            entry.modePerms.toString(8).padStart(4, '0')
                    val leafMode = modeString.encodeToByteArray()
                    leaf = GitTreeLeaf(
                        mode = leafMode,
                        path = Paths.get(entry.name).name,
                        sha = entry.sha
                    )
                }

                is Pair<*, *> -> {
                    leaf = GitTreeLeaf(
                        mode = "040000".toByteArray(),
                        path = entry.first as String,
                        sha = entry.second as String
                    )
                }

                else -> throw IOException("Internal error: entry has the wrong type")
            }

            tree.items.add(leaf)
        }

        sha = objectWrite(tree, repo)
        val parent = path.substringBeforeLast('/', missingDelimiterValue = "")
        val base = Paths.get(path).name
        contents.getValue(parent).add(Pair(base, sha))
    }

    return sha
}

fun commitCreate(
    repo: GitRepository,
    tree: String,
    parent: String?,
    author: String,
    timeStamp: ZonedDateTime,
    message: String
): String {
    val commit = GitCommit()
    commit.kvlm["tree"] = mutableListOf(tree.encodeToByteArray())
    if (parent != null) commit.kvlm["parent"] = mutableListOf(parent.encodeToByteArray())

    val message = message.trim() + "\n"
    val offset = timeStamp.offset.totalSeconds
    val hours = offset.floorDiv(3600)
    val minutes = (offset % 3600).floorDiv(60)
    val tz = String.format("%s%02d%02d", if (offset >= 0) "+" else "-", hours, minutes)

    val author = author + " ${timeStamp.toEpochSecond()} $tz"

    commit.kvlm["author"] = mutableListOf(author.encodeToByteArray())
    commit.kvlm["committer"] = mutableListOf(author.encodeToByteArray())
    commit.kvlm[null] = mutableListOf(message.encodeToByteArray())

    return objectWrite(commit, repo)
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
        val repo = if (write) repoFind() else null
        val sha = objectHash(File(path), type.toByteArray(), repo)

        println(sha)
    }
}

class Log : CliktCommand(name = "log") {
    val commit: String by argument(help = "Commit to start at.").default("HEAD")

    override fun help(context: Context) =
        "Display history of a given commit."

    override fun run() {
        val repo = repoFind()

        require(repo != null) { "No git repository was found." }

        val sha = objectFind(repo, commit)
        if (commit == "HEAD" && sha == null)
        {
            println("Your current branch does not have any commit yet.")
            return
        }
        require(sha != null) { "Could not find sha associated with name $commit" }

        println("digraph myGitLog{")
        println("   node[shape=rect]")
        logGraphviz(repo, sha, mutableSetOf())
        println("}")
    }
}

class LsTree : CliktCommand(name = "ls-tree") {


    val recursive: Boolean by option("-r", help = "Recurse into sub-trees")
        .flag(default = false)
    val tree: String by argument(help = "A tree-ish object.")

    override fun help(context: Context) =
        "Pretty-print a tree object."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }
        lsTree(repo, tree, recursive)
    }
}

class Checkout : CliktCommand(name = "checkout") {

    val commit: String by argument(help = "The commit or tree to checkout.")
    val path: String by argument(help = "The EMPTY directory to checkout on.")

    override fun help(context: Context) =
        "Checkout a commit inside of a directory."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }
        val sha = objectFind(repo, commit)
        require(sha != null) { "Could not find sha associated with name $commit" }
        var obj = objectRead(repo, sha)
        require(obj != null) { "No valid object named $commit" }
        if (obj.fmt.contentEquals("commit".toByteArray())) {
            obj = objectRead(
                repo,
                (obj as GitCommit).kvlm["tree"]!!.single().decodeToString()
            )
        }

        val pathObj = Paths.get(path)
        if (pathObj.exists()) {
            require(pathObj.isDirectory()) { "Not a directory $path" }
            require(pathObj.listDirectoryEntries().isEmpty()) { "Not empty $path" }
        } else {
            pathObj.createDirectory()
        }

        treeCheckout(repo, (obj as GitTree), pathObj.absolute())
    }
}

class ShowRef : CliktCommand(name = "show-ref") {

    override fun help(context: Context) =
        "List references."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }
        val refs = refList(repo)
        showRef(repo, refs, prefix = "refs")
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
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        if (name != null) {
            tagCreate(repo, name as String, obj, createTagObject)
        } else {
            val refs = refList(repo)

            require(Paths.get("tags") in refs.keys) { "Could not find tags folder." }

            @Suppress("UNCHECKED_CAST")
            showRef(repo, refs[Paths.get("tags")] as MutableMap<Path, Any?>, withHash = false)
        }
    }
}

class RevParse : CliktCommand(name = "rev-parse") {

    val type: String? by option("--mgit-type", help = "Specify the  expected type")
        .choice("blob", "commit", "tag", "tree")

    val name: String by argument(help = "The new tag's name")

    override fun help(context: Context) =
        "Parse revision (or other objects) identifiers"

    override fun run() {
        val fmt = type?.encodeToByteArray()

        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        println(objectFind(repo, name, fmt, follow = true))
    }
}

class LsFiles : CliktCommand(name = "ls-files") {

    val verbose: Boolean by option("--verbose", help = "Show everything").flag(default = false)

    override fun help(context: Context) =
        "List all the stage files"

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        val index = indexRead(repo)
        if (verbose) println("Index file format v${index.version}, containing ${index.entries.size} entries.")

        for (e in index.entries) {
            println(e.name)
            if (verbose) {
                val entryType = mapOf(
                    0b1000 to "regular file",
                    0b1010 to "symlink",
                    0b1110 to "git link"
                )[e.modeType]

                println("  $entryType with perms: ${e.modePerms}")
                println("  on blob: ${e.sha}")
                println(
                    "  created: ${Instant.fromEpochSeconds(e.cTime.first.toLong())}.${e.cTime.second}, modified: ${
                        Instant.fromEpochSeconds(
                            e.mTime.first.toLong()
                        )
                    }.${e.mTime.second}"
                )
                println("  device: ${e.dev}, inode: ${e.ino}")
                println("  user: ${e.uid}  group: ${e.gid}")
                println("  flags: stage=${e.flagStage} assume_valid=${e.flagAssumeValid}")
            }
        }

    }
}


class CheckIgnore : CliktCommand(name = "check-ignore") {

    val paths: List<String> by argument("path", help = "Paths to check").multiple()

    override fun help(context: Context) =
        "Check path(s) against ignore rules."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        val rules = gitignoreRead(repo)
        for (path in paths) {
            if (checkIgnore(rules, path)) {
                println(path)
            }
        }
    }
}


class Status : CliktCommand(name = "status") {


    override fun help(context: Context) =
        "Show the working tree status."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        val index = indexRead(repo)

        showStatusBranch(repo)
        showStatusHeadIndex(repo, index)
        println()
        showStatusIndexWorktree(repo, index)
    }
}


class Remove : CliktCommand(name = "rm") {

    val paths: List<String> by argument("path", help = "Paths to remove").multiple()

    override fun help(context: Context) =
        "Remove files from the working tree and the index."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        rm(repo, paths)
    }
}


class Add : CliktCommand(name = "add") {

    val paths: List<String> by argument("path", help = "Paths to add").multiple()

    override fun help(context: Context) =
        "Add files contents to the index."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        add(repo, paths)
    }
}

class Commit : CliktCommand(name = "commit") {

    val message: String by option("-m", help = "Message to associate with this commit").required()

    override fun help(context: Context) =
        "Record changes to the repository."

    override fun run() {
        val repo = repoFind()
        require(repo != null) { "No git repository was found." }

        val index = indexRead(repo)
        val tree = treeFromIndex(repo, index)
            ?: throw IOException("Could not find tree from index : $index")
        val author = getUserGitConfig(gitConfigRead())
            ?: "Example User <could.notFind@GitConfigFile.com"
        val commit = commitCreate(
            repo, tree, objectFind(repo, "HEAD"),
            author,
            ZonedDateTime.now(),
            message
        )

        val activeBranch = getActiveBranch(repo)
        if (activeBranch != null) {
            val path = repoFile(repo, Paths.get("refs/heads").resolve(activeBranch))
                ?: throw IOException("Could not find active branch path.")
            File(path.toString()).writeText(commit + "\n")
        } else {
            val path = repoFile(repo, Paths.get("HEAD"))
                ?: throw IOException("Could not find HEAD path.")
            File(path.toString()).writeText("\n")
        }

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

