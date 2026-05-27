package org.searchmob.engine

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class EngineRegistryTest {
    private val client = OkHttpClient()

    private class Free(override val id: String) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ) = EngineResult.Success(emptyList())
    }

    private class Keyed(override val id: String) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)
        override val requiresApiKey = true

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ) = EngineResult.Success(emptyList())
    }

    @Test
    fun freeEnabledAndKeyedInactiveByDefault() {
        val registry = EngineRegistry(listOf(Free("a"), Keyed("b")))
        assertEquals(listOf("a"), registry.activeEngines(client).map { it.first.id })
    }

    @Test
    fun keyedBecomesActiveWhenKeySupplied() {
        val registry =
            EngineRegistry(
                listOf(Keyed("b")),
                configs = mapOf("b" to EngineConfig("b", enabled = true, apiKey = "secret")),
            )
        val active = registry.activeEngines(client)
        assertEquals(listOf("b"), active.map { it.first.id })
        assertEquals("secret", active.first().second.apiKey)
    }

    @Test
    fun googleAdapterIsRejected() {
        try {
            EngineRegistry(listOf(Free("google")))
            fail("registering a Google adapter should be rejected")
        } catch (_: IllegalArgumentException) {
            // expected — Google is a permanent non-goal
        }
    }
}
