package pl.edu.agh

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import pl.edu.agh.init.GameInitConfig
import pl.edu.agh.utils.ConfigUtils

class GameInitConfigTest {

    @Test
    fun `it should parse game init config`() {
        assertDoesNotThrow {
            ConfigUtils.getConfigOrThrow<GameInitConfig>()
        }
    }
}
