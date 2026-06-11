package org.searchmob.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.searchmob.engine.aggregate.Aggregator
import org.searchmob.engine.aggregate.EngineStatus
import java.util.concurrent.atomic.AtomicInteger

class AggregatorTest {
    private val client = OkHttpClient()

    private fun ctx(timeoutMs: Long = 5_000L) = EngineContext(httpClient = client, timeoutMs = timeoutMs)

    private class FakeEngine(
        override val id: String,
        private val items: List<EngineResultItem>,
        private val delayMs: Long = 0,
        private val throwError: Boolean = false,
        private val correction: String? = null,
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
                return EngineResult.Success(items, correction)
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
            val out = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx())).results
            assertEquals(1, out.size)
            assertEquals(setOf("a", "b"), out[0].engines.toSet())
            assertEquals("from a", out[0].snippet)
        }

    @Test
    fun navigationalQueryPromotesOfficialSiteOverLiteralForumHits() =
        runTest {
            // Regression: for "threejs" the official three.js site (one engine, ranked last, dotted
            // title) must beat single-engine forum posts that literally contain "threejs". The nav
            // boost + separator bridging float it to the top instead of burying it.
            val e =
                FakeEngine(
                    "x",
                    listOf(
                        item("x", "https://gamedev.net/x", 0, title = "ThreeJS - GameDev.net"),
                        item("x", "https://stackoverflow.com/q", 1, title = "threejs on Stack Overflow"),
                        item("x", "https://threejs.org/", 2, title = "Three.js - JavaScript 3D Library"),
                    ),
                )
            val out = Aggregator().aggregate(SearchQuery("threejs"), listOf(e to ctx())).results
            assertEquals("https://threejs.org/", out[0].url)
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
            val first = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx())).results
            assertEquals("https://shared.com", first.first().url)
            val second = Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx(), b to ctx())).results
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
            assertEquals(listOf("https://ok.com"), out.results.map { it.url })
        }

    @Test
    fun allFailYieldsEmptyNoError() =
        runTest {
            val boom = FakeEngine("boom", emptyList(), throwError = true)
            assertTrue(Aggregator().aggregate(SearchQuery("q"), listOf(boom to ctx())).results.isEmpty())
        }

    @Test
    fun consensusCorrectionPicksMostFrequentAndIgnoresEchoOfQuery() =
        runTest {
            val a = FakeEngine("a", listOf(item("a", "https://a.com", 0)), correction = "john depp")
            val b = FakeEngine("b", listOf(item("b", "https://b.com", 0)), correction = "John Depp")
            val c = FakeEngine("c", listOf(item("c", "https://c.com", 0)), correction = "jon depp")
            val out = Aggregator().aggregate(SearchQuery("jon dep"), listOf(a to ctx(), b to ctx(), c to ctx()))
            assertEquals("john depp", out.correction)
        }

    @Test
    fun noCorrectionWhenNoneReported() =
        runTest {
            val a = FakeEngine("a", listOf(item("a", "https://a.com", 0)))
            assertEquals(null, Aggregator().aggregate(SearchQuery("q"), listOf(a to ctx())).correction)
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

    @Test
    fun engineStatusDistinguishesContributedEmptyAndFailed() =
        runTest {
            val alpha =
                FakeEngine("alpha", listOf(item("alpha", "https://a.com/1", 0), item("alpha", "https://a.com/2", 1)))
            val beta = FakeEngine("beta", emptyList()) // responded, but found nothing
            val gamma = FakeEngine("gamma", emptyList(), throwError = true) // failed: must differ from empty

            val out =
                Aggregator().aggregate(
                    SearchQuery("q"),
                    listOf(alpha to ctx(), beta to ctx(), gamma to ctx()),
                )

            val byName = out.engineStatus.associateBy { it.name }
            assertEquals(EngineStatus.CONTRIBUTED, byName.getValue("alpha").status)
            assertEquals(2, byName.getValue("alpha").count)
            assertEquals(EngineStatus.EMPTY, byName.getValue("beta").status)
            assertEquals(EngineStatus.FAILED, byName.getValue("gamma").status)
            // The search still succeeds on the working engine despite the failure.
            assertEquals(2, out.results.size)
        }

    @Test
    fun engineStatusMarksATimeoutAsFailed() =
        runTest {
            val slow = FakeEngine("slow", listOf(item("slow", "https://s.com/1", 0)), delayMs = 1_000)
            val out = Aggregator().aggregate(SearchQuery("q"), listOf(slow to ctx(timeoutMs = 10)))
            assertEquals(EngineStatus.FAILED, out.engineStatus.single().status)
        }
}
