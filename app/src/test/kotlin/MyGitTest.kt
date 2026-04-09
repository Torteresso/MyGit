import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

@Target(AnnotationTarget.FUNCTION)
annotation class SkipGitFolderCheck

class MyGitTest {

    @TempDir
    lateinit var workingDirectory: Path

    lateinit var userDir: String

    private val outContent = ByteArrayOutputStream()
    private val originalOut = System.out

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
    fun changeCurrentDir()
    {
        userDir = System.getProperty("user.dir")
        System.setProperty("user.dir", workingDirectory.toString())
    }

    @Order(2)
    @AfterEach
    fun restoreCurrentDir()
    {
        System.setProperty("user.dir", userDir)
    }

    @Order(1)
    @AfterEach
    fun checkGitFolderBasicStructure(testInfo: TestInfo)
    {
        val method = testInfo.testMethod.orElse(null)
        if (method?.getAnnotation(SkipGitFolderCheck::class.java) != null) {
            return
        }

        assertTrue(workingDirectory.resolve(".git").isDirectory())
        assertTrue(workingDirectory.resolve(".git/branches").isDirectory())
        assertTrue(workingDirectory.resolve(".git/objects").isDirectory())
        assertTrue(workingDirectory.resolve(".git/refs/tags").isDirectory())
        assertTrue(workingDirectory.resolve(".git/refs/heads").isDirectory())
        assertTrue(workingDirectory.resolve(".git/config").isReadable())
        assertTrue(workingDirectory.resolve(".git/HEAD").isReadable())
        assertTrue(workingDirectory.resolve(".git/description").isReadable())
    }

    @Test
    fun initCommand_InEmptyDir_MyGitDirIsCreated() {
        val command = Init()
        val result = command.test(workingDirectory.toString())

        assertEquals(result.statusCode, 0)

        assertEquals(workingDirectory.listDirectoryEntries().size, 1)

    }

    @Test
    @SkipGitFolderCheck
    fun initCommand_NonEmptyDir_MyGitDirIsReinitialize() {
        workingDirectory.resolve(".git").createDirectory()
        workingDirectory.resolve(".git/test.txt").createFile()
        val command = Init()

        assertThrows<IllegalArgumentException> { command.test(workingDirectory.toString() )}
    }

    @SkipGitFolderCheck
    @Test
    fun hashObjectCommand_ForBlobFile_PrintSha()
    {
        val testFile = workingDirectory.resolve("test.txt")
        testFile.writeText("This is a test.\n")
        assertTrue(testFile.isReadable())
        HashObject().test(testFile.toString())

        assertEquals("484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81\n", outContent.toString())
    }

    fun writeSomeTestsFilesInGitRepo()
    {
        val testFile = workingDirectory.resolve("test.txt")
        testFile.writeText("This is a test.\n")
        assertTrue(testFile.isReadable())

        Init().test(workingDirectory.toString())
        HashObject().test("-w $testFile")
    }

    @Test
    fun hashObjectCommand_ForBlobFileWithWrite_WriteAndPrintSha()
    {
       writeSomeTestsFilesInGitRepo()



        assertEquals("484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81\n", outContent.toString())
        assertTrue(workingDirectory.resolve(".git/objects/48/4ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81").isReadable())
    }

    @Test
    fun catFileCommand_ForExistingBlobFile_PrintItsContent()
    {
        writeSomeTestsFilesInGitRepo()
        outContent.reset()

        CatFile().test("blob 484ba93ef5b0aed5b72af8f4e9dc4cfd10ef1a81")

        assertEquals("This is a test.\n", outContent.toString())
    }

}