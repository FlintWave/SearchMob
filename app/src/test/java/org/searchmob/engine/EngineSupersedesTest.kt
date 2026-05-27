package org.searchmob.engine

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineSupersedesTest {
    private val client = OkHttpClient()

    private class Free(override val id: String) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ) = EngineResult.Success(emptyList())
    }

    private class KeyedSuperseding(
        override val id: String,
        override val supersedes: Set<String>,
    ) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)
        override val requiresApiKey = true

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ) = EngineResult.Success(emptyList())
    }

    @Test
    fun keyedAdapterSupersedesFreeCounterpartWhenKeyed() {
        val registry =
            EngineRegistry(
                listOf(Free("mojeek"), KeyedSuperseding("mojeek-api", setOf("mojeek"))),
                configs =
                    mapOf(
                        "mojeek" to EngineConfig("mojeek", enabled = true),
                        "mojeek-api" to EngineConfig("mojeek-api", enabled = true, apiKey = "k"),
                    ),
            )
        assertEquals(listOf("mojeek-api"), registry.activeEngines(client).map { it.first.id })
    }

    @Test
    fun freeCounterpartRemainsWhenApiNotKeyed() {
        val registry = EngineRegistry(listOf(Free("mojeek"), KeyedSuperseding("mojeek-api", setOf("mojeek"))))
        assertEquals(listOf("mojeek"), registry.activeEngines(client).map { it.first.id })
    }
}
