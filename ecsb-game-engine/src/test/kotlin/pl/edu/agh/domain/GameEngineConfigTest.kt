package pl.edu.agh.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import pl.edu.agh.engine.GameEngineConfig
import pl.edu.agh.utils.ConfigUtils

class GameEngineConfigTest {
    @Test
    fun `test game engine config existence and shape`() {
        assertDoesNotThrow {
            ConfigUtils.getConfigOrThrow<GameEngineConfig>()
        }
    }
}
