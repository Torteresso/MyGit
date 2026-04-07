import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MyGitTest {
    @Test
    fun alwaysTrue() {

    }

    @Test
    fun alwaysFalse() {
        assertEquals(2, 1)
    }

    @Test
    fun alwaysTrue2() {
        assertEquals(2, 2)
    }

    @Test
    fun alwaysFalse2() {
        assertEquals(2, 3)
    }
}