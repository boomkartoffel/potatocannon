package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.strategy.*
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import org.junit.jupiter.api.*
import kotlin.properties.Delegates
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TimingTest {

    var port by Delegates.notNull<Int>()

    var baseCannon by Delegates.notNull<Cannon>()

    @BeforeAll
    fun setUp() {
        port = Random.nextInt(30_000, 60_000)
        TestBackend.start(port)
        baseCannon = Cannon(
            baseUrl = "http://127.0.0.1:$port",
        )
    }

    @AfterAll
    fun tearDown() {
        TestBackend.stop()
    }

    @Test
    fun `12 Attempts take about 7 seconds with the increasing backoff`() {

        val timeoutPotato = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(11),
            RequestTimeout.of(100),
            QueryParam("id", "RetryTest"),
            QueryParam("returnOkAfter", "12"),
        )

        val perAttemptTimeoutMs = 100
        val retryCount = 11                 // 11 retries -> 12 attempts total
        val finalOkCostMs = 10              // estimate for the successful attempt
        val backoffSteps = listOf(10, 25, 50, 100, 200, 400, 600, 800, 1000, 1200, 1400)

        // Ideal path: 11 timed-out attempts + progressive backoffs + final success
        val baseMs = (perAttemptTimeoutMs * retryCount) + backoffSteps.sum() + finalOkCostMs

        // Expected misc overhead (scheduling, logging, GC, timing fuzz)
        val miscOverheadMs = 20 * (retryCount + 1)

        val targetMs = baseMs + miscOverheadMs

        val pctSlack = 0.01                     // ±1% headroom for CI/jitter
        val minMs = (targetMs * (1 - pctSlack)).toLong()  // lower bound
        val maxMs = (targetMs * (1 + pctSlack)).toLong()  // upper bound


        val elapsedMs = measureNanoTime {
            baseCannon
                .addSettings(expect12Attempts)
                .fire(timeoutPotato)
        } / 1_000_000

        println("12 attempts took $elapsedMs ms (expected ≈ $targetMs ms; window [$minMs, $maxMs] ms)")

        elapsedMs shouldBeGreaterThanOrEqual minMs
        elapsedMs shouldBeLessThanOrEqual maxMs
    }

    @Test
    fun `12 Attempts with no increasing backoff take about 1200ms`() {
        val timeoutPotato = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(11),
            RequestTimeout.of(100),
            RetryDelay(RetryDelayPolicy.NONE),
            QueryParam("id", "RetryTestNoBackoff"),
            QueryParam("returnOkAfter", "12"),
        )

        val perAttemptTimeoutMs = 100
        val retryCount = 11                 // 11 retries -> 12 attempts total
        val finalOkCostMs = 10              // estimate for the successful attempt

        // Ideal path: 11 timed-out attempts +  final success
        val baseMs = (perAttemptTimeoutMs * retryCount) + finalOkCostMs

        // Expected misc overhead (scheduling, logging, GC, timing fuzz)
        val miscOverheadMs = 10 * (retryCount + 1)

        val targetMs = baseMs + miscOverheadMs

        val pctSlack = 0.03                     // ±3% headroom for CI/jitter
        val minMs = (targetMs * (1 - pctSlack)).toLong()  // lower bound
        val maxMs = (targetMs * (1 + pctSlack)).toLong()  // upper bound

        val elapsedMs = measureTimeMillis {
            baseCannon
                .addSettings(expect12Attempts)
                .fire(timeoutPotato)
        }

        println("12 attempts took $elapsedMs ms (expected ≈ $targetMs ms; window [$minMs, $maxMs] ms)")

        elapsedMs shouldBeGreaterThanOrEqual minMs
        elapsedMs shouldBeLessThanOrEqual maxMs
    }

    @Test
    fun `12 Attempts with a constant retry of 100 ms take about 1200ms`() {

        val perAttemptTimeoutMs = 100L
        val retryCount = 11
        val finalOkCostMs = 5
        val retryDelay = 150L

        val timeoutPotato = Potato(
            method = HttpMethod.POST,
            path = "/timeout",
            RetryLimit(retryCount),
            RequestTimeout.of(perAttemptTimeoutMs),
            RetryDelay.ofMillis(retryDelay),
            QueryParam("id", "RetryTestConstantBackoff"),
            QueryParam("returnOkAfter", "12"),
        )


        // Ideal path: 11 timed-out attempts + constant backoff + final success
        val baseMs = (perAttemptTimeoutMs * retryCount) + (retryDelay * retryCount) + finalOkCostMs

        // Expected misc overhead (scheduling, logging, GC, timing fuzz)
        val miscOverheadMs = 10 * (retryCount + 1)

        val targetMs = baseMs + miscOverheadMs

        val pctSlack = 0.03                     // ±3% headroom for CI/jitter
        val minMs = (targetMs * (1 - pctSlack)).toLong()  // lower bound
        val maxMs = (targetMs * (1 + pctSlack)).toLong()  // upper bound

        val elapsedMs = measureTimeMillis {
            baseCannon
                .addSettings(expect12Attempts)
                .fire(timeoutPotato)
        }

        println("12 attempts took $elapsedMs ms (expected ≈ $targetMs ms; window [$minMs, $maxMs] ms)")

        elapsedMs shouldBeGreaterThanOrEqual minMs
        elapsedMs shouldBeLessThanOrEqual maxMs
    }

    @Test
    fun `12 Potatoes with Pacing take about 1000ms`() {
        val countPotatoes = 12
        val responseTime = 2              // estimate for the successful attempt
        val pacing = 250L

        val potatoes = Potato(
            method = HttpMethod.POST,
            path = "/test"
        ) * countPotatoes

        val baseMs = (responseTime * countPotatoes) + (pacing * (countPotatoes - 1))

        val miscOverheadMs = 10 * (countPotatoes)

        val targetMs = baseMs + miscOverheadMs

        val pctSlack = 0.03                     // ±3% headroom for CI/jitter
        val minMs = (targetMs * (1 - pctSlack)).toLong()  // lower bound
        val maxMs = (targetMs * (1 + pctSlack)).toLong()  // upper bound

        val elapsedMs = measureTimeMillis {
            baseCannon
                .addSettings(Pacing(pacing))
                .fire(potatoes)
        }

        println("12 Potatoes took $elapsedMs ms (expected ≈ $targetMs ms; window [$minMs, $maxMs] ms)")

        elapsedMs shouldBeGreaterThanOrEqual minMs
        elapsedMs shouldBeLessThanOrEqual maxMs
    }

    @Test
    fun `Pacing can be customized with data from CannonContext and according to a custom function`() {
        val countPotatoes = 10
        val responseTime = 2
        val pacing = listOf(0L, 0, 0, 800, 800, 1000, 1000, 2000, 2000)

        val funkyPacing = Pacing {
            val currentCall = it.get<Int>("call") ?: 0
            pacing[currentCall]
        }

        val increaseCallCounter = CaptureToContext.global("call") { _, ctx ->
            val currentCall = ctx.get<Int>("call") ?: 0
            currentCall + 1
        }

        val ctx = PotatoCannonContext().apply { this["call"] = -1 }

        val potatoes = Potato.post(
            path = "/test",
            increaseCallCounter
        ) * countPotatoes

        val baseMs = (responseTime * countPotatoes) + pacing.sum()

        val miscOverheadMs = 10 * (countPotatoes)

        val targetMs = baseMs + miscOverheadMs

        val pctSlack = 0.03                     // ±3% headroom for CI/jitter
        val minMs = (targetMs * (1 - pctSlack)).toLong()  // lower bound
        val maxMs = (targetMs * (1 + pctSlack)).toLong()  // upper bound

        val elapsedMs = measureTimeMillis {
            baseCannon
                .withGlobalContext(ctx)
                .addSettings(funkyPacing)
                .fire(potatoes)
        }

        println("$countPotatoes Potatoes took $elapsedMs ms (expected ≈ $targetMs ms; window [$minMs, $maxMs] ms)")

        elapsedMs shouldBeGreaterThanOrEqual minMs
        elapsedMs shouldBeLessThanOrEqual maxMs
    }

    @Test
    fun `GET request times 10 to test-wait takes at least 5 seconds in sequential mode`() {
        val potatoes = (1..10).map {
            Potato(
                method = HttpMethod.GET,
                path = "/test-wait"
            )
                .addExpectation(expect200StatusCode)
                .addExpectation(expectHelloResponseText)
        }

        val start = System.currentTimeMillis()
        baseCannon
            .addSettings(FireMode.SEQUENTIAL)
            .fire(potatoes)
        val end = System.currentTimeMillis()
        val durationMs = end - start

        println("Sequential duration: $durationMs ms")
        Assertions.assertTrue(durationMs >= 5000, "Expected at least 5 seconds")
    }

    @Test
    fun `GET request times 500 to test-wait takes less than 1000 ms in parallel mode`() {
        val potato = Potato(
            method = HttpMethod.GET, path = "/test-wait-parallel",
            expect200StatusCode,
            expectHelloResponseText,
            Logging.OFF
        )

        val timeParallelFullCapacity = measureTimeMillis {
            baseCannon
                .addSettings(ConcurrencyLimit(500))
                .addSettings(RetryLimit(100))
                .addSettings(RetryDelay(RetryDelayPolicy.NONE))
                .fire(potato * 500)
        }

        val timeParallelHalfCapacity = measureTimeMillis {
            baseCannon
                .addSettings(ConcurrencyLimit(250))
                .addSettings(RetryLimit(100))
                .addSettings(RetryDelay(RetryDelayPolicy.NONE))
                .fire(potato * 500)
        }

        timeParallelFullCapacity shouldBeLessThan 1000
        timeParallelHalfCapacity shouldBeGreaterThan 1000
        println("Time for full capacity: $timeParallelFullCapacity, Time for half capacity: $timeParallelHalfCapacity")

    }

}