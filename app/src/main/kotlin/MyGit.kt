import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.arguments.optional
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
import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Formatter
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries
import kotlin.math.ceil


// IDEA TODO :
/*
* Write and interface with every function so I can implement a functional version vs imperative and compare them (performance, etc...)
* Write some tests for important functions
* Refactor into multiple files
* */

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
    val path = path.toRealPath()

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
    var kvlm: MutableMap<String?, MutableList<ByteArray>> = mutableMapOf()

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
    var items: MutableList<GitTreeLeaf> = mutableListOf()

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
    var kvlm: MutableMap<String?, MutableList<ByteArray>> = mutableMapOf()

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
        "Ambigous reference $name: Candidates are\n - ${
            shaAsList.joinToString(
                "\n - "
            )
        }."
    }

    var sha = shaAsList[0]


    if (fmt != null) return sha

    while (true) {
        require(sha != null) { "Sha should not be null for reference $name." }
        val obj = objectRead(repo, sha)

        if (obj?.fmt == fmt) return sha
        if (!follow) return null

        if (obj.fmt.contentEquals("tag".toByteArray())) {
            sha = (obj as GitTag).kvlm["object"]!!.single().decodeToString()
        } else if (obj.fmt.contentEquals("commit".toByteArray())
            && fmt.contentEquals("tree".toByteArray())
        ) {
            sha = (obj as GitCommit).kvlm["tree"]!!.single().decodeToString()
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

fun kvlmParse(
    raw: ByteArray, start: Int = 0,
    dct: MutableMap<String?, MutableList<ByteArray>> = mutableMapOf<String?, MutableList<ByteArray>>()
)
        : MutableMap<String?, MutableList<ByteArray>> {

    val spaceIndex = raw.sliceArray(start..<raw.size).indexOf(' '.code.toByte())
    val newLineIndex = raw.sliceArray(start..<raw.size).indexOf('\n'.code.toByte())

    if (spaceIndex !in 0..newLineIndex) {
        require(newLineIndex == start) { "Malformed commit : $raw" }
        dct[null] = mutableListOf(raw.sliceArray(start + 1..<raw.size))
        return dct
    }

    val key = raw.sliceArray(start..<spaceIndex)
    var end = start

    while (true) {
        end = raw.sliceArray((end + 1)..<raw.size).indexOf('\n'.code.toByte())
        if (raw.elementAt(end + 1) != ' '.code.toByte()) break
    }

    val value = raw.sliceArray((spaceIndex + 1)..<end).toString(Charsets.US_ASCII)
        .replace("\n ", "\n")
        .toByteArray(Charsets.US_ASCII)

    if (key.toString() in dct) {
        dct[key.toString()]!!.add(value)
    } else {
        dct[key.toString()] = mutableListOf(value)
    }

    return kvlmParse(raw, start = end + 1, dct = dct)
}

fun kvlmSerialize(kvlm: MutableMap<String?, MutableList<ByteArray>>): ByteArray {
    var ret = ByteArray(0)

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
        println("  c_$sha -> c_$p;")
        logGraphviz(repo, pDecoded, seen)
    }
}

data class GitTreeLeaf(val mode: ByteArray, val path: String, val sha: String) {}

fun treeParseOne(raw: ByteArray, start: Int = 0): Pair<Int, GitTreeLeaf> {
    val modeTerminatorIndex = raw.sliceArray(start..<raw.size).indexOf(' '.code.toByte())

    require(modeTerminatorIndex - start == 5 || modeTerminatorIndex - start == 6)
    { "Wrong position for mode terminator of the tree $raw" }

    var mode = raw.sliceArray(start..<modeTerminatorIndex)
    if (mode.size == 5) mode = "0".toByteArray() + mode

    val pathTerminatorIndex = raw.sliceArray(modeTerminatorIndex..<raw.size)
        .indexOf(0x00.toByte())

    val path = raw.sliceArray(modeTerminatorIndex + 1..<pathTerminatorIndex)

    val rawSha = ByteBuffer.wrap(raw, pathTerminatorIndex + 1, pathTerminatorIndex + 21).int

    val sha = rawSha.toUInt().toString(radix = 16)

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

    var ret: ByteArray = ByteArray(0)

    for (leaf in obj.items) {
        ret += leaf.mode
        ret += ' '.code.toByte()
        ret += leaf.path.encodeToByteArray()
        ret += 0x00.toByte()
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

        if ((recursive != null && recursive) || (typeName == "tree")) {
            println(
                "${"0".repeat(6 - item.mode.size) + item.mode.decodeToString()} $type ${item.sha}\t${
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
        if (v is String && withHash) {
            print("$v $prefix$k")
        } else if (v is String) {
            print("$prefix$k")
        } else {
            @Suppress("UNCHECKED_CAST")
            showRef(repo, v as MutableMap<Path, Any?>, withHash, "$prefix$k")
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
        tag.kvlm["tagger"] = mutableListOf("Mgit <mgit@example.com>".toByteArray())
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
        return mutableListOf<String?>(refResolve(repo, Paths.get("HEAD")))
    }

    if (hashRE.matches(name)) {
        val name = name.lowercase()
        val prefix = name.substring(0..<2)
        val path = repoDir(repo, Paths.get("objects"), Paths.get(prefix), mkdir = false)
        if (path != null) {
            val rem = name.substring(2..<name.length)
            for (f in path.listDirectoryEntries()) {
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
) {
}

data class GitIndex(
    val version: Int = 2,
    val entries: MutableList<GitIndexEnTry> = mutableListOf<GitIndexEnTry>()
) {

}

fun indexRead(repo: GitRepository): GitIndex {
    val indexFile = repoFile(repo, Paths.get("index"))

    if (indexFile == null || !indexFile.isReadable()) return GitIndex()

    val raw = File(indexFile.toString()).readBytes()

    val header = raw.sliceArray(0..<12)
    val signature = header.sliceArray(0..<4)
    require(signature.contentEquals("DIRC".toByteArray())) { "The signature is not DIRC." }
    val version = ByteBuffer.wrap(header, 0, 4).int
    require(version == 2) { "mgit only supports index file version 2" }
    val count = ByteBuffer.wrap(header, 8, 12).int

    val entries = mutableListOf<GitIndexEnTry>()

    val content = raw.sliceArray(12..<raw.size)
    var idx = 0
    for (i in 0..<count) {
        val cTimeS = ByteBuffer.wrap(content, idx, idx + 4).int
        val cTimeNs = ByteBuffer.wrap(content, idx + 4, idx + 8).int
        val mTimeS = ByteBuffer.wrap(content, idx + 8, idx + 12).int
        val mTimeNs = ByteBuffer.wrap(content, idx + 12, idx + 16).int
        val dev = ByteBuffer.wrap(content, idx + 16, idx + 20).int
        val ino = ByteBuffer.wrap(content, idx + 20, idx + 24).int
        val unused = ByteBuffer.wrap(content, idx + 24, idx + 26).int
        val mode = ByteBuffer.wrap(content, idx + 26, idx + 28).int
        val uid = ByteBuffer.wrap(content, idx + 28, idx + 32).int
        val gid = ByteBuffer.wrap(content, idx + 32, idx + 36).int
        val sha = ByteBuffer.wrap(content, idx + 36, idx + 40).int.toHexString()
        val fSize = ByteBuffer.wrap(content, idx + 40, idx + 60).int
        val flags = ByteBuffer.wrap(content, idx + 60, idx + 62).int

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
            print("Notice: Name is 0x$nameLength bytes long.")
            //        /!\ PROBABLY BROKEN /!\
            val nullIdx = content.sliceArray(idx + 0xFFF..<content.size).indexOf(0x00.toByte())
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

        println("digraph myGitLog{")
        println("   node[shape=rect]")
        val sha = objectFind(repo, commit)
        require(sha != null) { "Could not find sha associated with name $commit" }
        logGraphviz(repo, sha, mutableSetOf())
        println("}")
    }
}

class LsTree : CliktCommand(name = "ls-tree") {


    val recursive: Boolean by option("-r", help = "Recurse into sub-trees")
        .flag(default = true)
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
        .flag(default = true)
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

        print(objectFind(repo, name, fmt, follow = true))
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
        .main(args)
} catch (e: IOException) {
    System.err.println("IOException at ${e.stackTrace.first().lineNumber}: ${e.message}")
} catch (e: IllegalArgumentException) {
    System.err.println("IllegalArgumentException at ${e.stackTrace.first().lineNumber}: ${e.message}")
}

