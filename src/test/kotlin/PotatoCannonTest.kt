package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.deserialization.EnumDefaultValue
import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingFailureException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.DeserializationFormat
import io.github.boomkartoffel.potatocannon.result.Deserializer
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.*
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.*
import java.util.stream.Stream
import kotlin.properties.Delegates
import kotlin.random.Random
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

data class CreateUser(
    val id: Int, val name: String, val email: String
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
        val finalOkCostMs = 10              // your estimate for the successful attempt
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
            RetryDelayPolicy.NONE,
            QueryParam("id", "RetryTestNoBackoff"),
            QueryParam("returnOkAfter", "12"),
        )

        val perAttemptTimeoutMs = 100
        val retryCount = 11                 // 11 retries -> 12 attempts total
        val finalOkCostMs = 10              // your estimate for the successful attempt

        // Ideal path: 11 timed-out attempts + progressive backoffs + final success
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
                .addSettings(RetryDelayPolicy.NONE)
                .fire(potato * 500)
        }

        val timeParallelHalfCapacity = measureTimeMillis {
            baseCannon
                .addSettings(ConcurrencyLimit(250))
                .addSettings(RetryLimit(100))
                .addSettings(RetryDelayPolicy.NONE)
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
            body = TextBody("{ }"),
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
            body = TextBody("{ \"message\": \"hi\" }"),
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
            val created = result.bodyAsObject(CreateUser::class.java, DeserializationFormat.XML)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML")

        val expectList = Check { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java, DeserializationFormat.XML)

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
            val enum = result.bodyAsObject(EmptyEnumCheckObject::class.java, DeserializationFormat.XML)
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
            val created = result.bodyAsObject(CreateUser::class.java, DeserializationFormat.XML, Charsets.UTF_32)
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
            val created = result.bodyAsObject(CreateUser::class.java, DeserializationFormat.XML)
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
            .withBody(TextBody("test"))
            .addExpectation(noContentTypeRequestHeader)

        val utf16Potato = basePotato
            .withBody(TextBody("test", Charsets.UTF_16))
            .addExpectation(noContentTypeRequestHeader)

        val utf16PotatoWithContentType = basePotato
            .withBody(TextBody("test", Charsets.UTF_16))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charSetIsNotSet)

        val utf16PotatoWithContentTypeAndCharset = basePotato
            .withBody(TextBody("test", Charsets.UTF_16, true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charSetIsUtf16)

        val invalidUtf16PotatoWithCharsetAndNoContentType = basePotato
            .withBody(TextBody("test", Charsets.UTF_16, true))

        val utf8Potato = basePotato
            .withBody(TextBody("test", true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charsetIsUtf8)

        val invalidContentTypeSettingPotato = basePotato
            .withBody(TextBody("test", true))

        val utf8PotatoWithTextPlain = basePotato
            .withBody(TextBody("test", true))
            .addSettings(ContentType.TEXT_PLAIN)
            .addExpectation(charsetIsUtf8)
            .addExpectation(textPlainIsSet)


        val test2 = basePotato
            .withBody(TextBody("test", true))
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
            body = TextBody("{ }"),
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
            method = HttpMethod.POST, body = TextBody("{ }"), path = "/test", settings = listOf(
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
            method = HttpMethod.POST, body = TextBody("{ }"), path = "/test", settings = listOf(
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
            body = TextBody("{}"),
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
            .withBody(TextBody(""))
            .addSettings(hasContentLengthZero)
            .addSettings(LogCommentary("Empty String Body"))

        val binaryPotato = basePotato
            .withBody(BinaryBody(ByteArray(0)))
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
            CaptureToContext(keyForSecondCall) {
                it.responseText()
            },
            CaptureToContext("attempts") {
                it.attempts
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
                QueryParam("number", ctx.get<String>(keyForSecondCall))
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
            .withContext(CannonContext().also { it["test"] = "test" })
            .fire(
                firstPotato, secondPotato
            )

    }

    @Test
    fun `Resolving a key from context that is not present or of the wrong type will throw a RequestPreparationException`() {

        val missingKey = Potato(
            method = HttpMethod.POST,
            path = "/test",
            resolveFromContext { ctx ->
                QueryParam("missing", ctx["missing"])
            },
        )

        val context = CannonContext().apply { this["intKey"] = 1 }

        val wrongCast = Potato(
            method = HttpMethod.POST,
            path = "/test",
            resolveFromContext { ctx ->
                val wrongType: String = ctx["intKey"]
                QueryParam("wrongType", wrongType)
            },
        )

        shouldThrow<RequestPreparationException> {
            baseCannon.fire(missingKey)
        }.message shouldBe "No value found in context for the key 'missing'"

        shouldThrow<RequestPreparationException> {
            baseCannon
                .withContext(context)
                .fire(wrongCast)
        }.message shouldBe "Value for 'intKey' is of type java.lang.Integer, but expected is java.lang.String"
    }

    @Test
    fun `Binary Body can be sent`() {
        val binaryContent = ByteArray(1024) { it.toByte() } // 1 KB of binary data

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = BinaryBody(binaryContent),
            ContentType.OCTET_STREAM,
            expect200StatusCode,
            expectHelloResponseText
        )


        baseCannon.fire(potato)
    }


}
