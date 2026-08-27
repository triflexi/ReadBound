package app.readbound.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class AiTranslationServiceTest {
    @Test
    fun appendsChatCompletionsToApiRoot() {
        assertEquals(
            "https://example.test/v1/chat/completions",
            AiTranslationService.chatCompletionsEndpoint("https://example.test/v1/"),
        )
    }

    @Test
    fun preservesFullChatCompletionsEndpoint() {
        assertEquals(
            "http://10.0.2.2:11434/v1/chat/completions",
            AiTranslationService.chatCompletionsEndpoint("http://10.0.2.2:11434/v1/chat/completions"),
        )
    }
}
