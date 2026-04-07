import com.github.ajalt.clikt.testing.test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectory
import kotlin.io.path.createFile
import kotlin.io.path.isDirectory
import kotlin.io.path.isReadable
import kotlin.io.path.listDirectoryEntries

@Target(AnnotationTarget.FUNCTION)
annotation class SkipAfterEach

class MyGitTest {

    @TempDir
    lateinit var workingDirectory: Path

    @AfterEach
    fun checkGitFolderBasicStructure(testInfo: TestInfo)
    {
        val method = testInfo.testMethod.orElse(null)
        if (method?.getAnnotation(SkipAfterEach::class.java) != null) {
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
    @SkipAfterEach
    fun initCommand_NonEmptyDir_MyGitDirIsReinitialize() {
        workingDirectory.resolve(".git").createDirectory()
        workingDirectory.resolve(".git/test.txt").createFile()
        val command = Init()

        assertThrows<IllegalArgumentException> { command.test(workingDirectory.toString() )}
    }


}