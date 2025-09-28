package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.annotation.*
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingFailureException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.marshalling.Deserializer
import io.github.boomkartoffel.potatocannon.marshalling.EnumDefaultValue
import io.github.boomkartoffel.potatocannon.marshalling.WireFormat
import io.github.boomkartoffel.potatocannon.potato.*
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.*
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.time.*
import java.util.stream.Stream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory
import kotlin.properties.Delegates
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

@XmlRoot(value = "User")
data class CreateUser(
    val id: Int,
    val name: String,
    val email: String
)

data class EmptyStringToNullCheckObject(
    val user: CreateUser?
)

enum class EmptyEnumCheck {
    @EnumDefaultValue
    NONE,
    SOME,

}

data class EmptyEnumCheckObject(
    val enum: EmptyEnumCheck,
    val enum2: EmptyEnumCheck
)

data class NullCheckObject(
    val map: Map<String, String>,
    val list: List<String>,
    val string: String = "",
    val int: Int = 0
)

data class JavaTimeCheckObject(
    val localDate: LocalDate,
    val localTime: LocalTime,
    val localDateTime: LocalDateTime,
    val offsetTime: OffsetTime,
    val offsetDateTime: OffsetDateTime,
    val zonedDateTime: ZonedDateTime,
    val instant: java.time.Instant,
    val year: Year,
    val yearMonth: YearMonth,
    val monthDay: MonthDay,
    val duration: Duration,
    val period: Period,
    val zoneId: ZoneId,
    val zoneOffset: ZoneOffset
)

object CommaSeparatedDeserializer : Deserializer {
    override fun <T> deserializeObject(data: String, targetClass: Class<T>): T {
        require(targetClass == CreateUser::class.java) {
            "Unsupported class for CommaSeparatedSingleDeserializer: $targetClass"
        }

        val parts = data.trim().split(",")
        if (parts.size != 3) error("Expected 3 fields for CreateUser")

        return CreateUser(
            id = parts[0].trim().toInt(), name = parts[1].trim(), email = parts[2].trim()
        ) as T
    }

    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        require(targetClass == CreateUser::class.java) {
            "Unsupported class for CommaSeparatedListDeserializer: $targetClass"
        }

        return data.trim().split(";").filter { it.isNotBlank() }.map { line ->
            deserializeObject(line, CreateUser::class.java) as T
        }
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PotatoCannonTest {

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
    fun `GET request to test returns Hello`() {

        val potato = Potato(
            method = HttpMethod.GET, path = "/test", expectHelloResponseText, expect200StatusCode
        )

        val cannon = baseCannon.withFireMode(FireMode.SEQUENTIAL)

        cannon.fire(potato)
    }

    @ParameterizedTest(name = "{index}: {1}")
    @MethodSource("illegalUrlAndHeaderSettings")
    fun `Requests with illegal URI or illegal header are not executed`(
        setting: PotatoSetting,
        expectedMessageContains: String
    ) {
        val potato = Potato(method = HttpMethod.GET, path = "/test")
            .addSettings(setting)

        val ex = shouldThrow<RequestPreparationException> {
            baseCannon.fire(potato)
        }
        ex.message shouldContain expectedMessageContains
    }

    fun illegalUrlAndHeaderSettings(): Stream<Arguments> {
        return Stream.of(
            Arguments.of(
                OverrideBaseUrl("127.0.0.1"),
                "Unsupported or missing scheme"
            ),
            Arguments.of(
                OverrideBaseUrl("ftp://example.com"),
                "Unsupported or missing scheme"
            ),
            Arguments.of(
                OverrideBaseUrl("https:///nohost"),
                "URL must be absolute and include a host"
            ),
            Arguments.of(
                OverrideBaseUrl("http://exa mple.com"),
                "Invalid URL syntax"
            ),
            Arguments.of(
                CustomHeader("", "value"),
                "Header name is empty"
            ),
            Arguments.of(
                CustomHeader("Invalid:Header", "value"),
                "Invalid header name"
            ),
            Arguments.of(
                CustomHeader("X-Test", "line1\nline2"),
                "contains CR/LF"
            ),
            Arguments.of(
                CustomHeader("X-Test", "bad\u0001char"),
                "contains control characters"
            )
        )
    }


    @Test
    fun `Requests to non-existing server will have http request errors`() {

        val nonExistingBase = Potato(
            method = HttpMethod.GET, path = "/test",
            OverrideBaseUrl("http://127.0.0.1:9999")
        )

        shouldThrow<RequestSendingFailureException> {
            baseCannon
                .addSettings(RetryLimit(5))
                .fire(nonExistingBase)
        }.message shouldContain "Failed to send GET request to http://127.0.0.1:9999/test within 6 attempts"

    }

    @Test
    fun `Requests are retried until Max Retry is reached`() {

        val timeoutPotato = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(100)
        )

        shouldThrow<RequestSendingFailureException> {
            baseCannon
                .fire(timeoutPotato)
        }.message shouldContain "Failed to send POST request to http://127.0.0.1:$port/timeout within 6 attempts"
    }

    @Test
    fun `Sequential Requests are retried in order`() {

        val timeoutPotato1 = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(300),
            QueryParam("id", "Test1"),
            QueryParam("returnOkAfter", "4"),
            LogCommentary("First potato, should appear before second potato in log")
        )

        val timeoutPotato2 = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(150),
            QueryParam("id", "Test2"),
            QueryParam("returnOkAfter", "4"),
            LogCommentary("Second potato, should appear after first potato in log")
        )

        baseCannon
            .addSettings(expect4Attempts)
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(timeoutPotato1, timeoutPotato2)
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
            val currentCall = ctx.get<Int>("call")?: 0
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
    fun `Deserialization attempts at responses with no body fail with NoBodyException`() {

        val tryConversionOnNullBodyFails = Check {
            it.bodyAsObject(CreateUser::class.java)
        }

        val basePotato = Potato(
            method = HttpMethod.GET, path = "/no-body"
        )

        shouldNotThrow<ResponseBodyMissingException> {
            baseCannon.fire(basePotato.addSettings(expectResponseBodyIsMissing))
        }

        shouldThrow<ResponseBodyMissingException> {
            baseCannon.fire(basePotato.addSettings(tryConversionOnNullBodyFails))
        }
    }


    @Test
    fun `Log commentary appears first for cannon log comments and then for potato comments in order of settings`() {
        val potato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            LogCommentary("Third Commentary - Potato"),
            LogCommentary("Fourth Commentary - Potato")
        )

        baseCannon
            .addSettings(
                LogCommentary("First Commentary - Cannon"),
                LogCommentary("Second Commentary - Cannon")
            )
            .fire(potato)

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

    @Test
    fun `POST request with some body is printed`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextPotatoBody("{ }"),
            ContentType.JSON,
            expect200StatusCode,
            expectHelloResponseText
        )

        baseCannon.fire(potato)
    }

    @Test
    fun `POST request from readme`() {
        val cannon = baseCannon
            .addSettings(
                BasicAuth("user", "pass")
            )

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextPotatoBody("{ \"message\": \"hi\" }"),
            settings = listOf(
                ContentType.JSON,
                Expectation("Status Code is 200 and return value is Hello") { result ->
                    assertEquals(200, result.statusCode)
                    assertEquals("Hello", result.responseText())
                })
        )

        cannon.fire(potato)
    }

    @Test
    fun `POST request to create user returns serializable JSON`() {
        val expect = Check { result: Result ->
            val created = result.bodyAsObject(CreateUser::class.java)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single JSON object")

        val expectList = Check { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java)
            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from JSON List")

        val potatoSingleObject = Potato(
            method = HttpMethod.POST, path = "/create-user", expect
        )

        val potatoList = potatoSingleObject.withPath("/create-user-list").withSettings(expectList)

        baseCannon.fire(potatoSingleObject, potatoList)
    }

    @Test
    fun `POST request to create user returns serializable XML`() {
        val expectSingle = Check { result: Result ->
            val created = result.bodyAsObject(CreateUser::class.java, WireFormat.XML)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML")

        val expectList = Check { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java, WireFormat.XML)

            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from XML as List")

        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/create-user-xml", expectSingle
        )

        val potatoList = potatoSingle.withPath("/create-user-xml-list").withSettings(expectList)


        baseCannon.fire(potatoSingle, potatoList)

    }

    @Test
    fun `XML with various attributes gets correctly printed to the log`() {
        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/xml-data-with-attributes"
        )

        baseCannon.fire(potatoSingle)

    }

    @Test
    fun `POST request to create user that returns a json with an unknown field fails on fail mode and works on ignore mode`() {
        val check = Check { result: Result ->
            result.bodyAsObject(CreateUser::class.java)
        }

        val failMapping = UnknownPropertyMode.FAIL
        val ignoreMapping = UnknownPropertyMode.IGNORE

        val potato = Potato(
            method = HttpMethod.POST, path = "/create-user-json-unknown-field",
            check
        )


        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(
                potato
                    .addSettings(failMapping)
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is ignore
                potato,
                potato
                    .addSettings(ignoreMapping)
            )
        }

    }

    @Test
    fun `GET request for NullCheckObject fails on strict mode and works on relax mode`() {
        val check = Check { result: Result ->
            result.bodyAsObject(NullCheckObject::class.java)
        }

        val strictNullCheck = NullCoercion.STRICT
        val relaxNullCheck = NullCoercion.RELAX

        val partialObjectPotato = Potato(
            method = HttpMethod.GET, path = "/null-check-object-partial",
            check
        )
        val fullObjectPotato = partialObjectPotato
            .withPath("/null-check-object-full")


        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is strict
                partialObjectPotato,
                partialObjectPotato
                    .addSettings(strictNullCheck),
                //default is strict
                fullObjectPotato,
                fullObjectPotato
                    .addSettings(strictNullCheck)
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                partialObjectPotato
                    .addSettings(relaxNullCheck),
                fullObjectPotato
                    .addSettings(relaxNullCheck)
            )
        }

    }

    @Test
    fun `GET request for JavaTimeCheckObject works by default`() {
        val check = Check { result: Result ->
            result.bodyAsObject(JavaTimeCheckObject::class.java)
        }

        val potato = Potato(
            method = HttpMethod.GET,
            path = "/time-object",
            check
        )


        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is disabled
                potato,
            )
        }

    }

    @Test
    fun `deserialization respects CaseInsensitiveProperties (fails by default, succeeds when enabled)`() {
        val check = Check { result: Result ->
            result.bodyAsList(CreateUser::class.java)
        }

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/create-user-case-different",
            check
        )

        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is disabled
                potato
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                potato
                    .addSettings(CaseInsensitiveProperties)
            )
        }
    }

    @Test
    fun `deserialization respects CaseInsensitiveEnums (fails by default, succeeds when enabled)`() {
        val check = Check { result: Result ->
            val body = result.bodyAsObject(EmptyEnumCheckObject::class.java)
            body.enum shouldBe EmptyEnumCheck.NONE
            body.enum2 shouldBe EmptyEnumCheck.SOME
        }.withDescription("Response is correctly deserialized and enum values are matched ignoring case")

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/case-insensitive-enum",
            check
        )

        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is disabled
                potato
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                potato
                    .addSettings(CaseInsensitiveEnums)
            )
        }
    }

    @Test
    fun `deserialization respects AcceptEmptyStringAsNullObject (fails by default, succeeds when enabled)`() {
        val check = Check { result: Result ->
            val emptyUser = result.bodyAsObject(EmptyStringToNullCheckObject::class.java)
            emptyUser.user shouldBe null
        }.withDescription("Response is correctly deserialized and empty string is mapped to null")

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/create-user-empty-string",
            check
        )

        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is disabled
                potato
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                potato
                    .addSettings(AcceptEmptyStringAsNullObject)
            )
        }
    }

    @Test
    fun `deserialization sets enum to default values`() {
        val checkJson = Check { result: Result ->
            val enum = result.bodyAsObject(EmptyEnumCheckObject::class.java)
            enum.enum shouldBe EmptyEnumCheck.NONE
            enum.enum2 shouldBe EmptyEnumCheck.NONE
        }

        val checkXml = Check { result: Result ->
            val enum = result.bodyAsObject(EmptyEnumCheckObject::class.java, WireFormat.XML)
            enum.enum shouldBe EmptyEnumCheck.NONE
            enum.enum2 shouldBe EmptyEnumCheck.NONE
        }

        val typeXmlHeader = QueryParam("type", "xml")

        val potatoJson = Potato(
            method = HttpMethod.POST,
            path = "/empty-enum",
            checkJson
        )

        val potatoXml = potatoJson
            .withPath("/empty-enum")
            .withSettings(typeXmlHeader, checkXml)

        val potatoWithUnknownValueJson = potatoJson
            .withPath("/empty-enum-and-not-matched")

        val potatoWithUnknownValuesXml = potatoXml
            .withPath("/empty-enum-and-not-matched")
            .withSettings(typeXmlHeader, checkXml)

        //default is disabled
        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(potatoJson)
        }
        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(potatoWithUnknownValueJson)
        }
        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(potatoXml)
        }
        shouldThrow<DeserializationFailureException> {
            baseCannon.fire(potatoWithUnknownValuesXml)
        }


        shouldNotThrow<DeserializationFailureException> {
            baseCannon
                .addSettings(UnknownEnumAsDefault)
                .fire(
                    potatoJson,
                    potatoWithUnknownValueJson,
                    potatoXml,
                    potatoWithUnknownValuesXml
                )
        }
    }

    @Test
    fun `POST request to create user returns serializable XML and can be deserialized with a different charset`() {
        val expectSingle = Check { result: Result ->
            val created = result.bodyAsObject(CreateUser::class.java, WireFormat.XML, Charsets.UTF_32)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML with a UTF32 charset")

        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/create-user-xml-utf32", expectSingle
        )

        baseCannon.fire(potatoSingle)
    }

    @Test
    fun `POST request to create user returns serializable XML and can be deserialized with a different charset automatically`() {
        val expectSingle = Check { result: Result ->
            val created = result.bodyAsObject(CreateUser::class.java, WireFormat.XML)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML and the Charset UTF-32 is automatically detected and applied")

        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/create-user-xml-utf32", expectSingle
        )

        baseCannon.fire(potatoSingle)
    }

    @Test
    fun `POST request to create user with custom formatting can be serialized with custom mapper`() {
        val expectSingle = Check { result: Result ->
            val created = result.bodyAsObject(CreateUser::class.java, CommaSeparatedDeserializer)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single csv line")

        val expectList = Check { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java, CommaSeparatedDeserializer)

            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from csv as List")

        val potatoSingle = Potato(
            method = HttpMethod.POST, path = "/create-user-custom", expectSingle
        )

        val potatoList = potatoSingle.withPath("/create-user-custom-list").withSettings(expectList)


        baseCannon.fire(potatoSingle, potatoList)

    }

    @Test
    fun `POST requests with Header Strategy is working correctly`() {
        val cannon = baseCannon.withFireMode(FireMode.PARALLEL)

        val appendPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CustomHeader("Append-Header", "AppendValue"),
            CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.APPEND),
            Expectation("Header Append Check -> List elements are correct") { result ->
                result.requestHeaders["Append-Header"] shouldContainExactly listOf("AppendValue", "AppendValue2")
            },
            Expectation("Header Append Check -> contains 2 elements") { result ->
                result.requestHeaders["Append-Header"].size shouldBe 2
            })

        val newPot = appendPotato
            .addExpectation(expect200StatusCode)

        val overwritePotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CustomHeader("Append-Header", "AppendValue"),
            CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.OVERWRITE),
            Expectation("Header Append Check -> contains 1 element") { result ->
                result.requestHeaders["Append-Header"] shouldContainExactly listOf("AppendValue2")
                result.requestHeaders["Append-Header"].size shouldBe 1
            })

        cannon.fire(newPot, overwritePotato)

    }

    @Test
    fun `Textbody will be using correct charset`() {
        val cannon = baseCannon.withFireMode(FireMode.PARALLEL)

        val basePotato = Potato(
            method = HttpMethod.POST,
            path = "/test"
        )

        val charsetIsUtf8 = Expectation("Charset is UTF-8") { result ->
            result.requestHeaders["Content-Type"].first().shouldContain("charset=UTF-8")
        }

        val noContentTypeRequestHeader = Expectation("No Content-Type request header is set") { result ->
            result.requestHeaders["Content-Type"].shouldBeEmpty()
        }

        val charSetIsNotSet = Expectation("Charset is not set") { result ->
            result.requestHeaders["Content-Type"].first().shouldNotContain("charset=UTF-8")
        }
        val charSetIsUtf16 = Expectation("Charset is UTF-16") { result ->
            result.requestHeaders["Content-Type"].first().shouldContain("charset=UTF-16")
        }
        val textPlainIsSet = Expectation("Content-Type is text/plain") { result ->
            result.requestHeaders["Content-Type"].first().shouldContain("text/plain")
        }

        val defaultPotato = basePotato
            .withBody(TextPotatoBody("test"))
            .addExpectation(noContentTypeRequestHeader)

        val utf16Potato = basePotato
            .withBody(TextPotatoBody("test", Charsets.UTF_16))
            .addExpectation(noContentTypeRequestHeader)

        val utf16PotatoWithContentType = basePotato
            .withBody(TextPotatoBody("test", Charsets.UTF_16))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charSetIsNotSet)

        val utf16PotatoWithContentTypeAndCharset = basePotato
            .withBody(TextPotatoBody("test", Charsets.UTF_16, true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charSetIsUtf16)

        val invalidUtf16PotatoWithCharsetAndNoContentType = basePotato
            .withBody(TextPotatoBody("test", Charsets.UTF_16, true))

        val utf8Potato = basePotato
            .withBody(TextPotatoBody("test", true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charsetIsUtf8)

        val invalidContentTypeSettingPotato = basePotato
            .withBody(TextPotatoBody("test", true))

        val utf8PotatoWithTextPlain = basePotato
            .withBody(TextPotatoBody("test", true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charsetIsUtf8)
            .addExpectation(textPlainIsSet)


        val test2 = basePotato
            .withBody(TextPotatoBody("test", true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charsetIsUtf8)
            .addExpectation(textPlainIsSet)
            .addSettings(CustomHeader("VAL", "foo", HeaderUpdateStrategy.APPEND))
            .addSettings(CustomHeader("VAL", "bar", HeaderUpdateStrategy.APPEND))
            .addSettings(OverrideBaseUrl("https://app.beeceptor.com/console/dfdfvfsdsdef"))

        cannon.fire(
            defaultPotato,
            utf8Potato,
            utf16Potato,
            utf8PotatoWithTextPlain,
            utf16PotatoWithContentType,
            utf16PotatoWithContentTypeAndCharset,
            test2
        )

        shouldThrow<RequestPreparationException> { cannon.fire(invalidContentTypeSettingPotato) }
        shouldThrow<RequestPreparationException> { cannon.fire(invalidUtf16PotatoWithCharsetAndNoContentType) }

    }

    @Test
    fun `Requests can be set as HTTP_1_1 or HTTP_2 to Servers that accept them and the client will enforce the protocol`() {
        val basePotato = Potato(
            method = HttpMethod.GET,
            path = "/",
            OverrideBaseUrl("https://nghttp2.org"),
            expect200StatusCode,
            LogExclude.BODY
        )

        val isHttp1 = Expectation("Response was HTTP/1.1") { result ->
            result.protocol.family shouldBe ProtocolFamily.HTTP_1_1
            result.protocol.major shouldBe 1
            result.protocol.minor shouldBe 1
        }

        val isHttp2 = Expectation("Response was HTTP/2") { result ->
            result.protocol.family shouldBe ProtocolFamily.HTTP_2
            result.protocol.major shouldBe 2
            result.protocol.minor shouldBe 0
        }

        val http1Potato =
            basePotato.addSettings(HttpProtocolVersion.HTTP_1_1, LogCommentary("Request with HTTP/1.1"), isHttp1)
        val http2Potato =
            basePotato.addSettings(HttpProtocolVersion.HTTP_2, LogCommentary("Request with HTTP/2"), isHttp2)
        val negotiatedPotato = basePotato.addSettings(
            HttpProtocolVersion.NEGOTIATE,
            LogCommentary("Request with negotiated protocol"),
            isHttp2
        )


        baseCannon.fire(
            http1Potato,
            http2Potato,
            negotiatedPotato
        )

    }

    @Test
    fun `Enforcing HTTP2 on a server that doesn't allow it will not work`() {
        val potato = Potato(
            method = HttpMethod.GET,
            path = "/",
            HttpProtocolVersion.HTTP_2,
        )

        shouldThrow<RequestSendingFailureException> {
            baseCannon.fire(
                potato,
            )
        }.message shouldContain "Connection is closed"

    }


    @Test
    fun `POST requests have correct logging`() {
        val headers = listOf(
            ContentType.JSON,
            QueryParam("query", "value"),
            BearerAuth("sometoken"),
            CustomHeader("X-Custom-Header", "CustomValue"),
            CookieHeader("1234567890abcdef")
        )

        val basePotato = Potato(
            method = HttpMethod.POST,
            path = "/test-logging",
            body = TextPotatoBody("{ }"),
        )

        val baseLoggingPotatoes = Logging.values().map { logging ->
            basePotato.withSettings(
                headers + logging + Expectation(
                    "This is logging: $logging", {

                    })
            )
        }

        val logExcludeCombinations = buildSet {
            val H = LogExclude.HEADERS
            val B = LogExclude.BODY
            val Q = LogExclude.QUERY_PARAMS
            val S = LogExclude.SECURITY_HEADERS
            val F = LogExclude.FULL_URL
            val V = LogExclude.VERIFICATIONS

            add(emptySet())
            add(setOf(H))
            add(setOf(B))
            add(setOf(Q))
            add(setOf(S))
            add(setOf(F))
            add(setOf(V))

            add(setOf(H, B))
            add(setOf(H, Q))
            add(setOf(H, S))
            add(setOf(B, Q))
            add(setOf(B, S))
            add(setOf(Q, S))
            add(setOf(B, V))

            add(setOf(F, H))
            add(setOf(F, B))
            add(setOf(F, Q))

            add(setOf(H, B, Q))
            add(setOf(H, B, S))
            add(setOf(H, Q, S))
            add(setOf(B, Q, S))
            add(setOf(H, B, Q, S))
            add(setOf(H, B, Q, S, V))
        }

        val baseLoggingPotatoesWithExcludes = logExcludeCombinations.map { logExcludes ->
            basePotato.withSettings(
                headers + Logging.FULL + logExcludes + Expectation(
                    "This is FULL logging with excludes: $logExcludes", {
                        println("↓\uFE0F This is FULL logging with excludes: $logExcludes")
                    })
            )
        }

        val cannon = baseCannon.withFireMode(FireMode.SEQUENTIAL)

        cannon.fire(baseLoggingPotatoes + baseLoggingPotatoesWithExcludes)
    }


    @Test
    fun `POST with multiple headers will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST, body = TextPotatoBody("{ }"), path = "/test", settings = listOf(
                ContentType.JSON,
                ContentType.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                expectHelloResponseText,
                Expectation("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"].size shouldBe 1
                    result.requestHeaders["Content-Type"].first() shouldBe "application/xml"
                },
                Expectation("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"].size shouldBe 1
                    result.requestHeaders["Authorization"].first() shouldBe "Bearer mytoken"
                })
        )

        val cannon = baseCannon.addSettings(
            listOf(
                BasicAuth(
                    username = "user", password = "password"
                ),
                expect200StatusCode,
                QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
    }


    @Test
    fun `POST with alternate base path is sending the request to a different host`() {
        val randomLetters = (1..30).map { ('a'..'z').random() }.joinToString("")

        val alternateBeeceptorUrl = "https://$randomLetters.free.beeceptor.com"

        val expectBeceptorUrl = Expectation("Beeceptor URL is used") { result ->
            result.fullUrl shouldContain "$alternateBeeceptorUrl/test"
        }

        val defaultPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            expect200StatusCode,
            expectHelloResponseText,
            expectHostToBeLocalhost
        )

        val overrideBaseUrlPotato = defaultPotato.withSettings(
            OverrideBaseUrl(alternateBeeceptorUrl), expectBeceptorUrl, expectHostNotToBeLocalhost
        )

        baseCannon.fire(defaultPotato, overrideBaseUrlPotato)
    }

    @Test
    fun `POST with multiple headers to mockserver will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST, body = TextPotatoBody("{ }"), path = "/test", settings = listOf(
                ContentType.JSON,
                ContentType.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                Expectation("Returns default beeceptor nothing configured yet message") { result ->
                    result.responseText() shouldBe "Hey ya! Great to see you here. BTW, nothing is configured here. Create a mock server on Beeceptor.com"
                },
                Expectation("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"].size shouldBe 1
                    result.requestHeaders["Content-Type"].first() shouldBe "application/xml"
                },
                Expectation("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"].size shouldBe 1
                    result.requestHeaders["Authorization"].first() shouldBe "Bearer mytoken"
                })
        )

        val randomLetters = (1..30).map { ('a'..'z').random() }.joinToString("")

        val randomBeeceptorUrl = "$randomLetters.free.beeceptor.com"

        val beeceptorCannon = Cannon(
            baseUrl = "https://$randomBeeceptorUrl",
            BasicAuth(
                username = "user", password = "password"
            ),
            CustomHeader("X-Custom-Header", "CustomValue"),
            expect400StatusCode,
            QueryParam("queryCannon", "valueCannon")

        )

        beeceptorCannon.fire(potato)
    }

    @Test
    fun `Query Params are URL encoded`() {
        val potato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            QueryParam("           query", "value with spaces & special characters like ? and ="),
            QueryParam("query2", "value2 with < or ÜÖÄ> special characters"),
        )

        val expect = Expectation("Characters are URL encoded") { result ->
            result.fullUrl shouldContain "test?+++++++++++query=value+with+spaces+%26+special+characters+like+%3F+and+%3D&query2=value2+with+%3C+or+%C3%9C%C3%96%C3%84%3E+special+characters"
        }

        baseCannon.fire(potato.addSettings(expect))
    }

    @Test
    fun `POST with some return values`() {
        val potato = Potato(
            method = HttpMethod.POST, path = "/create-user",

            ContentType.JSON, expect200StatusCode
        )

        baseCannon.fire(potato)
    }

    @Test
    fun `POST with all kinds of methods`() {
        val potatoes = HttpMethod.values().map {
            Potato(
                method = it, path = "/not-available-endpoint", settings = listOf(
                    expect400StatusCode
                )
            )
        }.toList()

        baseCannon.fire(potatoes)

    }

    @Test
    fun `TRACE request does not allow a request body`() {
        val potato = Potato(
            method = HttpMethod.TRACE,
            body = TextPotatoBody("{}"),
            path = "/not-available-endpoint",
            settings = listOf(
                expect400StatusCode
            )
        )

        shouldThrow<RequestPreparationException> {
            baseCannon.fire(potato)
        }.message shouldBe "TRACE requests must not include a request body"
    }

    @Test
    fun `An empty Body will have a content-length of 0, a null body will have no content-length header`() {
        val hasContentLengthZero = Expectation("Content-Length is 0") { result ->
            result.requestHeaders["Content-Length"].first() shouldBe "0"
        }

        val hasNoContentLengthHeader = Expectation("No Content-Length header is set") { result ->
            result.requestHeaders["Content-Length"].shouldBeEmpty()
        }

        val basePotato = Potato(
            method = HttpMethod.POST,
            path = "/not-available-endpoint",
        )

        val emptyStringPotato = basePotato
            .withBody(TextPotatoBody(""))
            .addSettings(hasContentLengthZero)
            .addSettings(LogCommentary("Empty String Body"))

        val binaryPotato = basePotato
            .withBody(BinaryPotatoBody(ByteArray(0)))
            .addSettings(hasContentLengthZero)
            .addSettings(LogCommentary("Empty Binary Body"))

        val noBodyPotato = basePotato
            .addSettings(hasNoContentLengthHeader)
            .addSettings(LogCommentary("No Body at all"))

        baseCannon.fire(
            emptyStringPotato,
            binaryPotato,
            noBodyPotato
        )
    }

    @Test
    fun `Requests can be chained and information can be shared by the CannonContext`() {
        val keyForSecondCall = "the-key"

        val firstPotato = Potato(
            method = HttpMethod.POST,
            path = "/first-call",
            expect200StatusCode,
            CaptureToContext.global(keyForSecondCall) { r, _ ->
                r.responseText()
            },
            CaptureToContext.global("attempts") { r, _ ->
                r.attempts
            },
            resolveFromContext { ctx ->
                ctx.get<String>("test") shouldBe "test"
                LogCommentary("We have a cannon context: ${ctx.get<String>("test")}")
            }
        )

        val secondPotato = Potato(
            method = HttpMethod.POST,
            path = "/second-call",
            expect200StatusCode,
            expectOKResponseText,
            resolveFromContext { ctx ->
                QueryParam("number", ctx.get<String>(keyForSecondCall) ?: "")
            },
            resolveFromContext { ctx ->
                LogCommentary("The previous request needed ${ctx.get<Int>("attempts")} attempts")
            },
            resolveFromContext { ctx ->
                ctx.get<String>("test") shouldBe "test"
                LogCommentary("We have a cannon context: ${ctx.get<String>("test")}")
            }

        )

        baseCannon
            .withFireMode(FireMode.SEQUENTIAL)
            .withGlobalContext(PotatoCannonContext().also { it["test"] = "test" })
            .fire(
                firstPotato, secondPotato
            )

    }

    @Test
    fun `The cannon can be configured with context from call before`() {

        val authKey = "auth-key"

        val setAuthHeader = resolveFromContext {
            val token = it.get(authKey, String::class.java)
            if (token != null) {
                BearerAuth(token)
            } else {
                null
            }
        }

        val authPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CaptureToContext.global(authKey) { result ->
                result.responseText()
            }
        )

        val authCannon = baseCannon
            .withGlobalContext()
            .addSettings(setAuthHeader)

        authCannon.fire(authPotato)

        val expectAuthHeader = Check {
            it.requestHeaders["authorization"] shouldContain "Bearer Hello"
        }.withDescription("Bearer auth is set in the auth cannon")

        val validatePotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            expectAuthHeader
        )

        authCannon
            .fire(validatePotato)
    }

    @Test
    fun `Requests can be chained and a Body can be constructed from the CannonContext`() {
        val ctxKey = "the-key"

        val expectHelloInRequest = Expectation("Has the word Hello in Request Body") { result ->
            result.requestBody.shouldBeTypeOf<TextPotatoBody> {
                it.getContentAsString() shouldContain "Hello"
            }
        }

        val firstPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            expect200StatusCode,
            CaptureToContext.session(ctxKey) { r ->
                r.responseText()
            }
        )

        val secondPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            BodyFromContext {
                val content = it.get<String>(ctxKey)
                TextPotatoBody(
                    """
                {
                    "valueFromFirstCall": "$content"
                }
                """.trimIndent()
                )
            },
            expectHelloInRequest
        )

        baseCannon
            .addSettings(UseSessionContext())
            .fire(firstPotato)
            .fire(secondPotato)

    }


    @Test
    fun `A key can be retrieved from a Cannon context that is placed on the cannon level`() {

        val context = PotatoCannonContext().apply { this["key"] = "valueFromContext" }
        val queryParamIsAvailable = Expectation("Query param from context is available") { result ->
            result.fullUrl shouldContain "available=valueFromContext"
        }

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            resolveFromContext { ctx ->
                QueryParam("available", ctx["key"] ?: "")
            },
            queryParamIsAvailable
        )

        shouldNotThrow<RequestPreparationException> {
            baseCannon
                .withGlobalContext(context)
                .fire(potato)
        }

    }

    @Test
    fun `An object can be serialized by the cannon`() {

        @io.github.boomkartoffel.potatocannon.annotation.JsonRoot("TestRoot")
        data class Test1(val x: String = "1")

        //No JsonRoot annotation
        data class Test2(val x: String = "2")

        val testRootIsProvided = Check {
            it.requestBody?.getContentAsString() shouldContain "TestRoot"
        }.withDescription("TestRoot is provided in Serialization")

        val testRootMissing = Check {
            it.requestBody?.getContentAsString() shouldNotContain "TestRoot"
        }.withDescription("TestRoot is NOT provided in Serialization")

        val test1 = Test1()
        val test2 = Test2()


        val user = CreateUser(
            id = 1,
            name = "Max Muster",
            email = "m@muster.com"
        )
        val user2 = CreateUser(
            id = 2,
            name = "John Doe",
            email = "j@doe.com"
        )

        val printReqBody = Check {
            println(it.requestBody?.getContentAsString())
        }

        val otherData = NullCheckObject(
            map = mapOf("key1" to "value1", "key2" to "value2"),
            list = listOf("item1", "item2", "item3"),
        )

        val users = listOf(user, user2)
        val mixed = listOf(user, user2, otherData)

        val jsonPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            BodyFromObject.json(user),
        )
        val xmlPotato = jsonPotato.withBody(BodyFromObject.xml(user))

        val usersPotatoJson = jsonPotato.withBody(BodyFromObject.json(users))
        val usersPotatoXml = jsonPotato.withBody(BodyFromObject.xml(users))

        val mixedDataJson = jsonPotato.withBody(BodyFromObject.json(mixed))
        val mixedDataXml = jsonPotato.withBody(BodyFromObject.xml(mixed))

        val test1Potato = jsonPotato.withBody(BodyFromObject.json(test1)).addSettings(testRootIsProvided)
        val test2Potato = jsonPotato.withBody(BodyFromObject.json(test2)).addSettings(testRootMissing)

        baseCannon
            .withFireMode(FireMode.SEQUENTIAL)
            .addSettings(printReqBody)
            .fire(
                jsonPotato,
                xmlPotato,
                usersPotatoJson,
                usersPotatoXml,
                mixedDataJson,
                mixedDataXml,
                test1Potato,
                test2Potato
            )


    }

    @Test
    fun `JSON annotations are correctly used on serialization`() {
        // ── Models under test ───────────────────────────────────────────────────────
        @JsonRoot("TestRoot")
        data class Test1(val x: String = "1")

        // No JsonRoot → must serialize without wrapper
        data class Test2(val x: String = "2")

        // Rename a property for (de)serialization
        data class Renamed(
            @JsonName("userId") val id: Int = 7,
            val name: String = "Max"
        )

        data class OmitField(
            val x: String,
            val y: String,
            @JsonOmitNull val z: String? = null
        )

        data class Ignore(
            val x: String = "1",
            @JsonIgnore
            val y: String = "2",
        )

        class IgnoreClass(
            val x: String = "1",
            @JsonIgnore
            val y: String = "2",
        )

        // class-level
        @JsonOmitNull
        data class OmitNullOnClass(
            val x: String? = null,
            val y: String? = null
        )

        // Control JSON property order
        @JsonPropertyOrder("b", "a")
        data class Ordered(val a: Int = 1, val b: Int = 2)


        // ── Expectations ────────────────────────────────────────────
        // Root wrapping appears only when @JsonRoot present
        val testRootProvided = Check {
            it.requestBody?.getContentAsString()!!.shouldContain("TestRoot")
        }.withDescription("TestRoot is provided in Serialization")

        val testRootMissing = Check {
            it.requestBody?.getContentAsString()!!.shouldNotContain("TestRoot")
        }.withDescription("TestRoot is NOT provided in Serialization")

        // JsonName renames "id" -> "userId"
        val renamedHasUserId = Check {
            val s = it.requestBody?.getContentAsString()!!
            s.shouldContain("userId")
            s.shouldNotContain("id")
        }.withDescription("@JsonName renames id -> userId")

        val omitNullTestObject = OmitField(
            x = "x",
            y = "y",
            z = null
        )

        val omitNullTest1 = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""x":"x"""")
            body.shouldContain(""""y":"y"""")
            body.shouldNotContain(""""z"""")
            body.shouldNotContain(":null")
        }.withDescription("@JsonOmitNull omits null field when put on property level")

        val omitInClassObject = OmitNullOnClass(
            x = "x"
        )

        val omitNullTest2 = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            body.shouldContain(""""x":"x"""")
            body.shouldNotContain(""""y"""")
            body.shouldNotContain(":null")
        }.withDescription("@JsonOmitNull omits null field when put on class level")


        // JsonPropertyOrder enforces "b" before "a"
        val orderedKeys = Check {
            val s = it.requestBody?.getContentAsString()!!
            val bIdx = s.indexOf("b")
            val aIdx = s.indexOf("a")
            require(bIdx >= 0 && aIdx >= 0) { "Missing keys in JSON: $s" }
            require(bIdx < aIdx) { "Expected 'b' before 'a' in: $s" }
        }.withDescription("@JsonPropertyOrder puts b before a")


        val jsonIgnore = Check {
            val s = it.requestBody?.getContentAsString()!!
            s.shouldContain(""""x"""")
            s.shouldNotContain(""""y"""")
        }.withDescription("@JsonIgnore omits the field from serialization")

        // ── Potatoes ───────────────────────────────────────────────────────────────
        val basePotato = Potato(
            method = HttpMethod.POST,
            path = "/test"
        )

        val p1 = basePotato
            .withBody(BodyFromObject.json(Test1()))
            .addSettings(testRootProvided)

        val p2 = basePotato
            .withBody(BodyFromObject.json(Test2()))
            .addSettings(testRootMissing)

        val p3 = basePotato
            .withBody(BodyFromObject.json(Renamed()))
            .addSettings(renamedHasUserId)

        val p4 = basePotato
            .withBody(BodyFromObject.json(omitNullTestObject))
            .addSettings(omitNullTest1)

        val p5 = basePotato
            .withBody(BodyFromObject.json(omitInClassObject))
            .addSettings(omitNullTest2)

        val p6 = basePotato
            .withBody(BodyFromObject.json(Ordered()))
            .addSettings(orderedKeys)

        val p7 = basePotato
            .withBody(BodyFromObject.json(Ignore()))
            .addSettings(jsonIgnore)

        val p7b = basePotato
            .withBody(BodyFromObject.json(IgnoreClass()))
            .addSettings(jsonIgnore)

        baseCannon
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(p1, p2, p3, p4, p5, p6, p7, p7b)
//            .fire(p7)
    }

    @Test
    fun `JSON annotations are correctly used on deserialization`() {

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/mirror"
        )

        data class Ignored(
            val x: String,
            @JsonIgnore
            val y: String?,
        )

        val bodySentIgnored = """
            {
                "x": "value1",
                "y": "value2"
            }
        """.trimIndent()

        val expectIgnoreWorks = Check { result: Result ->
            val obj = result.bodyAsObject(Ignored::class.java)
            obj.x shouldBe "value1"
            obj.y shouldBe null
        }.withDescription("@JsonIgnore works on deserialization")

        data class JsonNameTest(
            @JsonName("a")
            val x: Int
        )

        val bodySentJsonName = """{"a":7}"""

        val expectJsonName = Check { result: Result ->
            val obj = result.bodyAsObject(JsonNameTest::class.java)
            assertEquals(7, obj.x)
        }.withDescription("@JsonName maps external name")

        data class AliasesTest(
            @JsonAliases("uid", "xid")
            val id: Int
        )

        val bodiesAliases = listOf(
            """{"uid":1}""",
            """{"xid":1}""",
            """{"id":1}"""
        )

        val expectJsonAlias = Check { result: Result ->
            val obj = result.bodyAsObject(AliasesTest::class.java)
            obj.id shouldBe 1
        }.withDescription("@JsonAliases maps alternative names")

        @JsonRoot("TestRoot")
        data class RootTest(val x: String)

        val bodySentRoot = """{"TestRoot":{"x":"abc"}}"""

        val expectRootTest = Check { result: Result ->
            val obj = result.bodyAsObject(RootTest::class.java)
            obj.x shouldBe "abc"
        }.withDescription("@JsonRoot deserialization unwrap")

        val bodySentRootList = """
        [
          {"TestRoot":{"x":"a"}},
          {"TestRoot":{"x":"b"}},
          {"TestRoot":{"x":"c"}}
        ]
    """.trimIndent()

        val expectRootList = Check { result: Result ->
            val items = result.bodyAsList(RootTest::class.java)
            items.size shouldBe 3
            items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("@JsonRoot deserialization unwrap for list elements")

        baseCannon
            .fire(
                potato.withBody(TextPotatoBody(bodySentIgnored)).addSettings(expectIgnoreWorks),
                potato.withBody(TextPotatoBody(bodySentJsonName)).addSettings(expectJsonName),
                *bodiesAliases.map {
                    potato.withBody(TextPotatoBody(it)).addSettings(expectJsonAlias)
                }.toTypedArray(),
                potato.withBody(TextPotatoBody(bodySentRoot)).addSettings(expectRootTest),
                potato.withBody(TextPotatoBody(bodySentRootList)).addSettings(expectRootList)
            )

    }

    @Test
    fun `XML annotations are correctly used on deserialization`() {
        val potato = Potato(method = HttpMethod.POST, path = "/mirror")

        @XmlRoot("TestRoot")
        data class RootTest(
            val x: String
        )

        val xmlRoot = """
                <TestRoot>
                  <x>abc</x>
                </TestRoot>
            """.trimIndent()

        val expectRoot = Check { result: Result ->
            val obj = result.bodyAsObject(RootTest::class.java, WireFormat.XML)
            obj.x shouldBe "abc"
        }.withDescription("@XmlRoot + @XmlElement basic object")

        data class User(
            @XmlElement("userId") val id: Int,
            val name: String
        )

        val xmlUser = """
                <User>
                  <userId>7</userId>
                  <name>Max</name>
                </User>
            """.trimIndent()

        val expectUser = Check { result: Result ->
            val u = result.bodyAsObject(User::class.java, WireFormat.XML)
            u.id shouldBe 7
            u.name shouldBe "Max"
        }.withDescription("@XmlElement renames field on read")

        data class Node(
            @XmlAttribute("id") val id: String,
            val value: String
        )

        val xmlNode = """<Node id="N1"><value>foo</value></Node>""".trimIndent()

        val expectNode = Check { result: Result ->
            val n = result.bodyAsObject(Node::class.java, WireFormat.XML)
            n.id shouldBe "N1"
            n.value shouldBe "foo"
        }.withDescription("@XmlAttribute maps attribute on read")

        val xmlBagWrapped = """
                <Bag>
                  <bagItems>
                    <item><x>a</x></item>
                    <item><x>b</x></item>
                    <item><x>c</x></item>
                  </bagItems>
                </Bag>
            """.trimIndent()

        val expectBagWrapped = Check { result: Result ->
            val bag = result.bodyAsObject(XmlModels.BagWrapped::class.java, WireFormat.XML)
            bag.items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("@XmlElementWrapper(useWrapping=true) reads wrapped list")

        val xmlBagUnwrapped = """
                <Bag>
                  <item><x>a</x></item>
                  <item><x>b</x></item>
                  <item><x>c</x></item>
                </Bag>
            """.trimIndent()


//        <item><x>a</x></item>
//        <item><x>b</x></item>
//        <item><x>c</x></item>

        val expectBagUnwrapped = Check { result: Result ->
            val bag = result.bodyAsObject(XmlModels.BagUnwrapped::class.java, WireFormat.XML)
            bag.items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("@XmlElementWrapper(useWrapping=false) reads flat list")

        val xmlRootList = """
                <RootTests>
                  <TestRoot><x>a</x></TestRoot>
                  <TestRoot><x>b</x></TestRoot>
                  <TestRoot><x>c</x></TestRoot>
                </RootTests>
            """.trimIndent()

        val expectRootList = Check { result: Result ->
            val items = result.bodyAsList(RootTest::class.java, WireFormat.XML)
            items.map { it.x } shouldBe listOf("a", "b", "c")
        }.withDescription("Top-level list of @XmlRoot elements")

        baseCannon
            .fire(
                potato.withBody(TextPotatoBody(xmlRoot)).addSettings(expectRoot),
                potato.withBody(TextPotatoBody(xmlUser)).addSettings(expectUser),
                potato.withBody(TextPotatoBody(xmlNode)).addSettings(expectNode),
                potato.withBody(TextPotatoBody(xmlBagWrapped)).addSettings(expectBagWrapped),
//                potato.withBody(TextPotatoBody(xmlBagUnwrapped)).addSettings(expectBagUnwrapped),
                potato.withBody(TextPotatoBody(xmlRootList)).addSettings(expectRootList)
            )
    }


    private object XmlModels {
        @XmlRoot("User")
        data class UserWithRoot(
            @XmlElement("UserId") val id: Int = 7,
            val name: String = "Max"
        )

        // No @XmlRoot -> should NOT be <User>
        data class UserNoRoot(val x: String = "1")

        // Attribute instead of element
        @XmlRoot("AttrUser")
        data class UserWithAttr(
            @XmlAttribute("id") val id: Int = 1,
            val name: String = "Max"
        )

        data class SimpleUser(val name: String)

        // Wrapper list
        @XmlRoot("Group")
        data class GroupWrapped(
            @XmlWrapperElement("Users")
            @XmlElement("User")
            val users: List<SimpleUser> = listOf(SimpleUser("A"), SimpleUser("B"))
        )

        // No wrapper list
        @XmlRoot("GroupFlat")
        data class GroupUnwrapped(
            @XmlUnwrap
            @XmlElement("User")
            val users: List<SimpleUser> = listOf(SimpleUser("A"), SimpleUser("B"))
        )


        // Text content (mixed attr + text)
        @XmlRoot("Note")
        data class Note(
            @XmlAttribute("lang") val lang: String = "en",
            @XmlText val text: String = "hello"
        )

        @XmlRoot("item")
        data class Item(@XmlElement("x") val x: String)

        @XmlRoot("Bag")
        data class BagWrapped(
            @XmlElement("bagItems")
            val items: List<Item>
        )

        @XmlRoot("Bag")
        data class BagUnwrapped(
            @XmlElementWrapper(useWrapping = false)
            val items: List<Item>
        )
    }

    @Test
    fun `XML annotations are correctly used`() {
        // ---------- Helpers ----------
        fun parseXml(s: String) = DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = false }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(s)))

        val xp = XPathFactory.newInstance().newXPath()
        fun rootName(doc: Document) = xp.evaluate("local-name(/*)", doc)
        fun count(doc: Document, expr: String) =
            xp.evaluate("count($expr)", doc).toDouble().toInt()

        fun text(doc: Document, expr: String) =
            xp.evaluate(expr, doc)

        // ---------- Expectations ----------
        val expectRootProvided = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) == "User") { "root should be <User>, was <${rootName(d)}>" }
            require(count(d, "/User/UserId") == 1) { "expected <UserId> element" }
        }.withDescription("@XmlRoot + @XmlElement rename work")

        val expectRootNotProvided = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) != "User") { "should not be <User> when @XmlRoot missing" }
        }.withDescription("No @XmlRoot does not produce <User>")

        val expectAttribute = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(rootName(d) == "AttrUser")
            require(text(d, "/AttrUser/@id") == "1") { "id attribute missing" }
            require(count(d, "/AttrUser/id") == 0) { "id must be attribute, not element" }
        }.withDescription("@XmlAttribute produces attribute, not element")

        val expectWrappedList = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            count(d, "/Group/Users/User") shouldBe 2
        }.withDescription("@XmlElementWrapper(name=Users) wraps list")

        val expectUnwrappedList = Check {
            val d = parseXml(it.requestBody?.getContentAsString().orEmpty())
            require(count(d, "/GroupFlat/Users") == 0) { "should not have <Users> wrapper" }
            require(count(d, "/GroupFlat/User") == 2) { "expected 2 unwrapped <User> items" }
        }.withDescription("@XmlElementWrapper(useWrapping=false) unwraps list")

        val expectTextContent = Check {
            val body = it.requestBody?.getContentAsString().orEmpty()
            val d = parseXml(body)
            require(text(d, "/Note/@lang") == "en")
            require(text(d, "normalize-space(/Note)") == "hello") { "text content missing" }
            body shouldNotContain "<text>"
            body shouldNotContain "</text>"
        }.withDescription("@XmlText puts value as element text")

        // ---------- Potatoes ----------
        val basePotato = Potato(method = HttpMethod.POST, path = "/test")

        val p1 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserWithRoot())).addSettings(expectRootProvided)
        val p2 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserNoRoot())).addSettings(expectRootNotProvided)
        val p3 = basePotato.withBody(BodyFromObject.xml(XmlModels.UserWithAttr())).addSettings(expectAttribute)
        val p4 = basePotato.withBody(BodyFromObject.xml(XmlModels.GroupWrapped())).addSettings(expectWrappedList)
        val p5 = basePotato.withBody(BodyFromObject.xml(XmlModels.GroupUnwrapped())).addSettings(expectUnwrappedList)
        val p6 = basePotato.withBody(BodyFromObject.xml(XmlModels.Note())).addSettings(expectTextContent)

        // ---------- Fire ----------
        baseCannon.withFireMode(FireMode.SEQUENTIAL).fire(p1, p2, p3, p4, p5, p6)
//        baseCannon.withFireMode(FireMode.SEQUENTIAL).fire( p3)
    }


    @Test
    fun `Binary Body can be sent`() {
        val binaryContent = ByteArray(1024) { it.toByte() } // 1 KB of binary data

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = BinaryPotatoBody(binaryContent),
            ContentType.OCTET_STREAM,
            expect200StatusCode,
            expectHelloResponseText
        )


        baseCannon.fire(potato)
    }

    @Test
    fun `file from test resources can be sent as multipart form-data`() {
        val resource = javaClass.getResource("/test.jpg")
            ?: error("Test resource not found: /test.jpg")
        val path = Paths.get(resource.toURI())
        val filename = path.fileName.toString()
        val fileBytes = Files.readAllBytes(path)

        // 2) Build multipart body
        val boundary = "----potato-${System.nanoTime()}"
        val bodyBytes = buildMultipartFormData(
            boundary = boundary,
            fileFieldName = "file",
            filename = filename,
            fileContent = fileBytes,
            fileMime = "application/octet-stream",
            extraFields = mapOf("note" to "uploaded from test")
        )

        // 3) Create Potato with correct Content-Type (include boundary!)
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = BinaryPotatoBody(bodyBytes),
            ContentType.OCTET_STREAM,
            expect200StatusCode
        )

        // 4) Fire it
        baseCannon.fire(potato)
    }

    private fun buildMultipartFormData(
        boundary: String,
        fileFieldName: String,
        filename: String,
        fileContent: ByteArray,
        fileMime: String,
        extraFields: Map<String, String> = emptyMap()
    ): ByteArray {
        val CRLF = "\r\n"
        val out = ByteArrayOutputStream()

        fun write(str: String) = out.write(str.toByteArray(Charsets.UTF_8))

        // Text fields first (optional)
        for ((name, value) in extraFields) {
            write("--$boundary$CRLF")
            write("Content-Disposition: form-data; name=\"$name\"$CRLF")
            write("Content-Type: text/plain; charset=UTF-8$CRLF$CRLF")
            write(value)
            write(CRLF)
        }

        // File field
        write("--$boundary$CRLF")
        write("Content-Disposition: form-data; name=\"$fileFieldName\"; filename=\"$filename\"$CRLF")
        write("Content-Type: $fileMime$CRLF$CRLF")
        out.write(fileContent)
        write(CRLF)

        // Closing boundary
        write("--$boundary--$CRLF")

        return out.toByteArray()
    }


}
