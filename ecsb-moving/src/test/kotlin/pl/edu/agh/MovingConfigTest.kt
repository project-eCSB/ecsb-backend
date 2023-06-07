package pl.edu.agh

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import pl.edu.agh.move.MovingConfig
import pl.edu.agh.utils.ConfigUtils

class MovingConfigTest {

    @Test
    fun `test moving config existence and shape`() {
        assertDoesNotThrow {
            ConfigUtils.getConfigOrThrow<MovingConfig>()
        }
    }
}
