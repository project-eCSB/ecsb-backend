package pl.edu.agh.chat

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import pl.edu.agh.utils.ConfigUtils

class ChatConfigTest {

    @Test
    fun `test chat config existence and shape`() {
        assertDoesNotThrow {
            ConfigUtils.getConfigOrThrow<ChatConfig>()
        }
    }
}
