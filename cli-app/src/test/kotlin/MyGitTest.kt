import com.github.ajalt.clikt.testing.test
import gitLogic.MyGitFunctions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.appendText
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Target(AnnotationTarget.FUNCTION)
annotation class SkipGitFolderCheck

class MyGitTest {

    private val gitCommands = MyGitFunctions()

    @TempDir
    lateinit var workingDirectory: Path

    lateinit var userDir: String

    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

    companion object {
        private const val SHA_LENGTH = 40
    }

    @BeforeEach
    fun changeOutPutStream() {
        System.setOut(PrintStream(outContent))
    }

    @Order(3)
    @AfterEach
    fun restoreOutPutStream() {
        System.setOut(originalOut)
    }

    @BeforeEach
    fun changeCurrentDir() {
        userDir = System.getProperty("user.dir")
        System.setProperty("user.dir", workingDirectory.toString())
    }

    @Order(2)
    @AfterEach
    fun restoreCurrentDir() {
        System.setProperty("user.dir", userDir)
    }

    @Order(1)
    @AfterEach
    fun checkGitFolderBasicStructure(testInfo: TestInfo) {
        val method = testInfo.testMethod.orElse(null)
        if (method?.getAnnotation(SkipGitFolderCheck::class.java) != null) {
            return
        }

        assertTrue(workingDirectory.resolve(".git").isDirectory())
        assertTrue(workingDirectory.resolve(".git/objects").isDirectory())
        assertTrue(workingDirectory.resolve(".git/refs/tags").isDirectory())
        assertTrue(workingDirectory.resolve(".git/refs/heads").isDirectory())
        assertTrue(workingDirectory.resolve(".git/config").isReadable())
        assertTrue(workingDirectory.resolve(".git/HEAD").isReadable())
        assertTrue(workingDirectory.resolve(".git/description").isReadable())
    }

    @Test
    fun initCommand_InEmptyDir_MyGitDirIsCreated() {
        val command = Init(gitCommands)
        val result = command.test(workingDirectory.toString())

        assertEquals(0, result.statusCode)

        assertEquals(1, workingDirectory.listDirectoryEntries().size)

    }

    @Test
    @SkipGitFolderCheck
    fun initCommand_NonEmptyDir_MyGitDirIsReinitialize() {
        workingDirectory.resolve(".git").createDirectory()
        workingDirectory.resolve(".git/test.txt").createFile()
        val command = Init(gitCommands)

        assertThrows<IllegalArgumentException> { command.test(workingDirectory.toString()) }
    }

    @SkipGitFolderCheck
    @Test
    fun hashObjectCommand_ForBlobFile_PrintSha() {
        val testFile = workingDirectory.resolve("test.txt")
        testFile.writeText("This is a test.\n")
        assertTrue(testFile.isReadable())
        HashObject().test(testFile.toString())

        assertEquals("484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81\n", outContent.toString())
    }

    fun writeSomeTestsFilesInGitRepo(): Path {
        val testFile = workingDirectory.resolve("test.txt")
        testFile.writeText("This is a test.\n")
        assertTrue(testFile.isReadable())

        Init(gitCommands).test(workingDirectory.toString())
        return testFile
    }

    @Test
    fun hashObjectCommand_ForBlobFileWithWrite_WriteAndPrintSha() {
        val testFile = writeSomeTestsFilesInGitRepo()
        HashObject().test("-w $testFile")



        assertEquals("484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81\n", outContent.toString())
        assertTrue(
            workingDirectory.resolve(".git/objects/48/4ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81")
                .isReadable()
        )
    }

    @Test
    fun catFileCommand_ForExistingBlobFile_PrintItsContent() {
        val testFile = writeSomeTestsFilesInGitRepo()
        HashObject().test("-w $testFile")
        outContent.reset()

        CatFile().test("blob 484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81")

        assertEquals("This is a test.\n", outContent.toString())
    }

    @Test
    fun logCommand_WithNoCommitYet_WarnUser() {
        Init(gitCommands).test(workingDirectory.toString())
        Log().test()

        assertEquals("Your current branch does not have any commit yet.\n", outContent.toString())
    }

    @Test
    fun commitCommand_InEmptyGitDir_CreateCommitAndEmptyTreeObject() {
        Init(gitCommands).test(workingDirectory.toString())

        Commit().test("-m \"Initial commit\"")

        assertTrue(workingDirectory.resolve(".git/refs/heads/master").isReadable())

        val treeSha = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"
        val commitSha =
            workingDirectory.resolve(".git/refs/heads/master").readText().removeSuffix("\n")

        assertEquals(SHA_LENGTH, commitSha.length)

        CatFile().test("commit $commitSha")

        val commitLines = outContent.toString().lines()

        assertEquals("tree $treeSha", commitLines[0])
        assertTrue(commitLines[1].contains("author"))
        assertTrue(commitLines[2].contains("committer"))
        assertEquals("Initial commit", commitLines[4])

        outContent.reset()
        CatFile().test("tree $treeSha")
        assertEquals("", outContent.toString())
    }

    @Test
    fun addCommand_ForOneFile_AddTheFileToGitDir() {
        val testFile = writeSomeTestsFilesInGitRepo()

        Commit().test("-m \"Initial commit\"")
        Add().test(testFile.toString())

        HashObject().test(testFile.toString())

        val fileSha = outContent.toString().removeSuffix("\n")

        assertEquals(SHA_LENGTH, fileSha.length)

        val fileShaDir = fileSha.substring(0..<2)
        val fileShaFile = fileSha.substring(2..<fileSha.length)

        assertTrue(workingDirectory.resolve(".git/objects/$fileShaDir/$fileShaFile").isReadable())
    }

    fun checkStatus(
        toBeCommited: String,
        notStaged: String,
        untrackedPresent: List<String> = emptyList(),
        untrackedAbsent: List<String> = emptyList()
    ) {
        Status().test()
        val statusLines = outContent.toString().split("\n\n")

        assertEquals(
            "On branch master.\n" +
                    "Changes to be committed:" + toBeCommited, statusLines[0]
        )
        assertEquals("Changes not staged for commit:$notStaged", statusLines[1])
        untrackedPresent.forEach { f -> assertTrue(statusLines[2].contains(f)) }
        untrackedAbsent.forEach { f -> assertFalse(statusLines[2].contains(f)) }

        outContent.reset()
    }

    @Test
    fun allCommands_ForTypicalUserFlow_ShouldNotThrow() {
        Init(gitCommands).test(workingDirectory.toString())
        Commit().test("-m \"Initial commit\"")

        val testFile = workingDirectory.resolve("test.txt")
        testFile.writeText("This is a test.\n")
        val gitignoreFile = workingDirectory.resolve(".gitignore")
        gitignoreFile.writeText("fileToIgnore.txt")
        val fileToIgnore = workingDirectory.resolve("fileToIgnore.txt")
        fileToIgnore.writeText("I don't want this file to be in my git repo.")

        checkStatus(
            "", "", listOf("fileToIgnore.txt", "test.txt", ".gitignore")
        )

        Add().test("$gitignoreFile $testFile")

        checkStatus(
            "\n  added:    .gitignore\n" +
                    "  added:    test.txt", "", untrackedAbsent = listOf("fileToIgnore.txt")
        )

        Commit().test("-m \"Add my files.\"")

        Tag().test()
        assertEquals("", outContent.toString())
        Tag().test("v1")
        Tag().test("-a v1Bis")
        Tag().test()
        assertEquals("v1\nv1Bis\n", outContent.toString())
        outContent.reset()

        ShowRef().test()
        val showRefLines = outContent.toString().lines()
        val masterSha = showRefLines[0].split(" ").first()
        val masterPath = showRefLines[0].split(" ")[1]
        val v1Ref = showRefLines[1].split(" ").first()
        val v1Path = showRefLines[1].split(" ")[1]
        val v1BisRef = showRefLines[2].split(" ").first()
        val v1BisPath = showRefLines[2].split(" ")[1]

        assertEquals("refs/heads/master", masterPath)
        assertEquals("refs/tags/v1", v1Path)
        assertEquals("refs/tags/v1Bis", v1BisPath)
        assertEquals(masterSha, v1Ref)
        assertTrue(v1Ref != v1BisRef)
        outContent.reset()

        CheckIgnore().test("${testFile.name} ${fileToIgnore.name} ${gitignoreFile.name}")
        assertEquals("fileToIgnore.txt\n", outContent.toString())
        outContent.reset()

        RevParse().test("--mgit-type commit HEAD")
        assertEquals(masterSha + "\n", outContent.toString())
        outContent.reset()

        testFile.appendText("Let's add some text to this file.")

        checkStatus("", "\n  modified:    test.txt", untrackedAbsent = listOf("fileToIgnore.txt"))

        Add().test(testFile.toString())
        Commit().test("-m \"Append some text to test.txt\"")

        Log().test()
        assertTrue(outContent.toString().contains("Add my files"))
        outContent.reset()

        LsTree().test("-r HEAD")
        val lsTreeLines = outContent.toString().lines()
        val gitIgnoreLineInfos = lsTreeLines[0].split(" ")
        val testFileLineInfos = lsTreeLines[1].split(" ")

        assertEquals(".gitignore", gitIgnoreLineInfos.last())
        assertEquals("test.txt", testFileLineInfos.last())
        assertEquals("100644", gitIgnoreLineInfos[0])
        assertEquals("100644", testFileLineInfos[0])
        assertEquals("blob", gitIgnoreLineInfos[1])
        assertEquals("blob", testFileLineInfos[1])

        val testFileSha = testFileLineInfos[2]
        outContent.reset()

        CatFile().test("blob $testFileSha")
        assertEquals("This is a test.\nLet's add some text to this file.", outContent.toString())

        LsFiles().test()
        assertTrue(outContent.toString().contains(".gitignore"))
        assertTrue(outContent.toString().contains("test.txt"))
        assertTrue(!outContent.toString().contains("fileToIgnore.txt"))
        outContent.reset()


        assertTrue(workingDirectory.resolve("test.txt").exists())
        val originalIn: InputStream = System.`in`
        try {
            val yesResponse = ByteArrayInputStream("y".toByteArray())
            System.setIn(yesResponse)
            Remove().test(testFile.toString())
        } finally {
            outContent.reset()
            System.setIn(originalIn)
        }

        assertTrue(!workingDirectory.resolve("test.txt").exists())

        checkStatus("\n  deleted:    test.txt", "", untrackedAbsent = listOf("fileToIgnore.txt"))

        Commit().test("-m \"Deleted the test file\"")

        checkStatus("", "", untrackedAbsent = listOf("fileToIgnore.txt"))

        val backUpDir = workingDirectory.resolve("BackUpDir")

        backUpDir.createDirectory()
        assertTrue(backUpDir.exists())

        Checkout().test("v1 $backUpDir")

        val testFileBackUp = workingDirectory.resolve("BackUpDir/test.txt")
        assertTrue(testFileBackUp.exists())
        assertTrue(workingDirectory.resolve("BackUpDir/.gitignore").exists())

        assertEquals("This is a test.\n", testFileBackUp.readText())
    }

}