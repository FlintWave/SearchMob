package org.searchmob.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.aggregate.Aggregator
import java.util.concurrent.atomic.AtomicInteger

class AggregatorTest {
    private val client = OkHttpClient()

    private fun ctx(timeoutMs: Long = 5_000L) = EngineContext(httpClient = client, timeoutMs = timeoutMs)

    private class FakeEngine(
        override val id: String,
        private val items: List<EngineResultItem>,
        private val delayMs: Long = 0,
        private val throwError: Boolean = false,
        private val onEnter: (() -> Unit)? = null,
        private val onExit: (() -> Unit)? = null,
    ) : EngineAdapter {
        override val displayName = id
        override val categories = setOf(SearchCategory.GENERAL)

        override suspend fun search(
            query: SearchQuery,
            ctx: EngineContext,
        ): EngineResult {
            onEnter?.invoke()
            try {
                if (delayMs > 0) delay(delayMs)
                if (throwError) throw RuntimeException("boom")
                return EngineResult.Success(items)
            } finally {
                onExit?.invoke()
            }
        }
    }

    private fun item(
        engine: String,
        url: String,
        position: Int,
        title: String = "T",
        snippet: String = "",
    ) = EngineResultItem(title = title, url = url, snippet = snippet, engineId = engine, position = position)

    @Test
    fun dedupCollapsesSameUrlAndRecordsEngines() =
        runTest {
            val a = FakeEngine("a", listOf(item("a", "https://example.com/x", 0, snippet = "from a")))
            val b = FakeEngine("b", listOf(item("b", "https://www.example.com/x/", 0)))
            val out = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx()))
            assertEquals(1, out.size)
            assertEquals(setOf("a", "b"), out[0].engines.toSet())
            assertEquals("from a", out[0].snippet)
        }

    @Test
    fun multiEngineAgreementRanksAboveSingleAndIsDeterministic() =
        runTest {
            val a =
                FakeEngine(
                    "a",
                    listOf(item("a", "https://shared.com", 0), item("a", "https://onlya.com", 1)),
                )
            val b = FakeEngine("b", listOf(item("b", "https://shared.com", 0)))
            val first = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx()))
            assertEquals("https://shared.com", first.first().url)
            val second = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx()))
            assertEquals(first.map { it.url }, second.map { it.url })
        }

    @Test
    fun failSoftWithErrorAndTimeoutStillMergesRest() =
        runTest {
            val ok = FakeEngine("ok", listOf(item("ok", "https://ok.com", 0)))
            val boom = FakeEngine("boom", emptyList(), throwError = true)
            val slow = FakeEngine("slow", listOf(item("slow", "https://slow.com", 0)), delayMs = 60_000)
            val out =
                Aggregator().aggregate(
                    SearchQuery("q"),
                    listOf(ok to ctx(1_000), boom to ctx(1_000), slow to ctx(1_000)),
                )
            assertEquals(listOf("https://ok.com"), out.map { it.url })
        }

    @Test
    fun allFailYieldsEmptyNoError() =
        runTest {
            val boom = FakeEngine("boom", emptyList(), throwError = true)
            assertTrue(Aggregator().aggregate(SearchQuery("q"), listOf(boom to ctx())).isEmpty())
        }

    @Test
    fun concurrencyNeverExceedsLimit() =
        runTest {
            val current = AtomicInteger(0)
            val maxSeen = AtomicInteger(0)
            val engines =
                (1..10).map { n ->
                    val id = "e$n"
                    FakeEngine(
                        id,
                        listOf(item(id, "https://$id.com", 0)),
                        delayMs = 100,
                        onEnter = {
                            val c = current.incrementAndGet()
                            maxSeen.updateAndGet { m -> maxOf(m, c) }
                        },
                        onExit = { current.decrementAndGet() },
                    ) to ctx(10_000)
                }
            Aggregator(maxConcurrent = 3).aggregate(SearchQuery("q"), engines)
            assertTrue("max in-flight was ${maxSeen.get()}", maxSeen.get() <= 3)
        }
}
