package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.strategy.BasicAuth
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.deserialization.EnumDefaultValue
import io.github.boomkartoffel.potatocannon.exception.DeserializationFailureException
import io.github.boomkartoffel.potatocannon.exception.ExecutionFailureException
import io.github.boomkartoffel.potatocannon.exception.RequestSendingException
import io.github.boomkartoffel.potatocannon.exception.ResponseBodyMissingException
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.strategy.ContentType
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.result.DeserializationFormat
import io.github.boomkartoffel.potatocannon.result.Deserializer
import io.github.boomkartoffel.potatocannon.strategy.AcceptEmptyStringAsNullObject
import io.github.boomkartoffel.potatocannon.strategy.OverrideBaseUrl
import io.github.boomkartoffel.potatocannon.strategy.BearerAuth
import io.github.boomkartoffel.potatocannon.strategy.CaseInsensitiveEnums
import io.github.boomkartoffel.potatocannon.strategy.CaseInsensitiveProperties
import io.github.boomkartoffel.potatocannon.strategy.ConcurrencyLimit
import io.github.boomkartoffel.potatocannon.strategy.CookieHeader
import io.github.boomkartoffel.potatocannon.strategy.CustomHeader
import io.github.boomkartoffel.potatocannon.strategy.HeaderUpdateStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.LogCommentary
import io.github.boomkartoffel.potatocannon.strategy.RetryLimit
import io.github.boomkartoffel.potatocannon.strategy.NullCoercion
import io.github.boomkartoffel.potatocannon.strategy.RequestTimeout
import io.github.boomkartoffel.potatocannon.strategy.UnknownEnumAsDefault
import io.github.boomkartoffel.potatocannon.strategy.UnknownPropertyMode
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.Period
import java.time.Year
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.collections.first
import kotlin.properties.Delegates
import kotlin.random.Random

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

    private val is200Expectation = Expectation("Response is 200 OK") { result ->
        result.statusCode shouldBe 200
    }

    private val isHelloResponseExpectation = Check { result ->
        result.responseText() shouldBe "Hello"
    }.withDescription("Response is Hello")


    private val is404Expectation = Check { result: Result ->
        result.statusCode shouldBe 404
    }.withDescription("Response is 404 Not Found")

    @Test
    fun `GET request to test returns Hello`() {

        val potato = Potato(
            method = HttpMethod.GET, path = "/test", isHelloResponseExpectation, is200Expectation
        )


        val cannon = baseCannon.withFireMode(FireMode.SEQUENTIAL)

        cannon.fire(potato)
    }

    @Test
    fun `Requests with illegal URI or illegal header are not executed`() {

        val basePotato = Potato(
            method = HttpMethod.GET, path = "/test"
        )

        val illegalUri = basePotato.addConfiguration(OverrideBaseUrl("127.0.0.1"))
        val illegalHeader = basePotato.addConfiguration(CustomHeader("Invalid:Header\n", "value"))

        val cannon = baseCannon.withFireMode(FireMode.SEQUENTIAL)

        shouldThrow<ExecutionFailureException> {
            cannon.fire(illegalUri)
        }
        shouldThrow<ExecutionFailureException> {
            cannon.fire(illegalHeader)
        }
    }

    @Test
    fun `Requests to non-existing server will have http request errors`() {

        val nonExistingBase = Potato(
            method = HttpMethod.GET, path = "/test",
            OverrideBaseUrl("http://127.0.0.1:9999")
        )

        shouldThrow<RequestSendingException> {
            baseCannon
                .addConfiguration(RetryLimit(5))
                .fire(nonExistingBase)
        }.message shouldBe "Failed to send request within 6 attempts"

    }

    @Test
    fun `Requests are retried until Max Retry is reached`() {

        val timeoutPotato = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(100)
        )

        shouldThrow<RequestSendingException> {
            baseCannon
                .fire(timeoutPotato)
        }.message shouldBe "Failed to send request within 6 attempts"
    }

    @Test
    fun `Sequential Requests are retried in order`() {

        val expect4Attempts = Check {
            it.attempts shouldBe 4
        }.withDescription("Request succeeded after 3 retries (4 attempts total)")

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
            .addConfiguration(expect4Attempts)
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(timeoutPotato1, timeoutPotato2)
    }

    @Test
    fun `12 Attempts take about 7 seconds with the increasing backoff`() {

        val expect4Attempts = Check {
            it.attempts shouldBe 12
        }.withDescription("Request succeeded after 11 retries (12 attempts total)")

        val timeoutPotato = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(11),
            RequestTimeout.of(100),
            QueryParam("id", "RetryTest"),
            QueryParam("returnOkAfter", "12"),
        )

        val totalTimeouts = 100 * 11
        val expectedVariousOverhead = 17 * 12 // about 17ms overhead/timeout inaccuracy per request
        val expectedFinalResponseTime = 10
        val timeouts = listOf(10, 25, 50, 100, 200, 400, 600, 800, 1000, 1200, 1400)

        val totalExpectedTime = totalTimeouts + expectedVariousOverhead + expectedFinalResponseTime + timeouts.sum()

        val start = System.currentTimeMillis()

        baseCannon
            .addConfiguration(expect4Attempts)
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(timeoutPotato)

        val end = System.currentTimeMillis()

        println("12 attempts took ${end - start} ms")
        println("Expected time was $totalExpectedTime ms")
        (end - start) shouldBeLessThan totalExpectedTime.toLong()
    }

    @Test
    fun `Parallel Requests are retried out of order (even if concurrency is disabled)`() {

        val expect4Attempts = Check {
            it.attempts shouldBe 4
        }.withDescription("Request succeeded after 3 retries (4 attempts total)")

        val timeoutPotato1 = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(300),
            QueryParam("id", "Test1Parallel"),
            QueryParam("returnOkAfter", "4"),
            LogCommentary("First potato, should appear as SECOND because of longer Request Timeout")
        )

        val timeoutPotato2 = Potato(
            method = HttpMethod.POST, path = "/timeout",
            RetryLimit(5),
            RequestTimeout.of(150),
            QueryParam("id", "Test2Parallel"),
            QueryParam("returnOkAfter", "4"),
            LogCommentary("Second potato, should appear FIRST in log due to shorter Request Timeout")
        )

        baseCannon
            .addConfiguration(expect4Attempts)
            .addConfiguration(ConcurrencyLimit(1))
            .withFireMode(FireMode.PARALLEL)
            .fire(timeoutPotato1, timeoutPotato2)
    }


    @Test
    fun `Deserialization attempts at responses with no body fail with NoBodyException`() {

        val checkBodyNull = Check {
            it.responseText() shouldBe null
        }
        val tryConversionOnNullBodyFails = Check {
            it.bodyAsObject(CreateUser::class.java)
        }

        val basePotato = Potato(
            method = HttpMethod.GET, path = "/no-body"
        )

        shouldNotThrow<ResponseBodyMissingException> {
            baseCannon.fire(basePotato.addConfiguration(checkBodyNull))
        }

        shouldThrow<ResponseBodyMissingException> {
            baseCannon.fire(basePotato.addConfiguration(tryConversionOnNullBodyFails))
        }
    }


    @Test
    fun `Log commentary appears first for cannon log comments and then for potato comments in order of configuration`() {
        val potato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            LogCommentary("Third Commentary - Potato"),
            LogCommentary("Fourth Commentary - Potato")
        )

        baseCannon
            .addConfiguration(
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
                .addExpectation(is200Expectation)
                .addExpectation(isHelloResponseExpectation)
        }

        val start = System.currentTimeMillis()
        baseCannon
            .addConfiguration(FireMode.SEQUENTIAL)
            .fire(potatoes)
        val end = System.currentTimeMillis()
        val durationMs = end - start

        println("Sequential duration: $durationMs ms")
        Assertions.assertTrue(durationMs >= 5000, "Expected at least 5 seconds")
    }

    @Test
    fun `GET request times 500 to test-wait takes less than 1150 ms in parallel mode`() {
        val potatoes = (1..500).map {
            Potato(
                method = HttpMethod.GET, path = "/test-wait-parallel", is200Expectation, isHelloResponseExpectation
            )
        }

        val start = System.currentTimeMillis()
        baseCannon.fire(potatoes)
        val end = System.currentTimeMillis()
        val durationMs = end - start

        println("Parallel duration: $durationMs ms")
        durationMs shouldBeLessThan 1150
    }

    @Test
    fun `POST request with some body is printed`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextBody("{ }"),
            ContentType.JSON,
            is200Expectation,
            isHelloResponseExpectation
        )

        baseCannon.fire(potato)
    }

    @Test
    fun `POST request from readme`() {
        val cannon = baseCannon
            .addConfiguration(
                listOf(
                    BasicAuth("user", "pass")
                )
            )

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextBody("{ \"message\": \"hi\" }"),
            configuration = listOf(
                ContentType.JSON, Expectation("Status Code is 200 and return value is Hello") { result ->
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

        val potatoList = potatoSingleObject.withPath("/create-user-list").withConfiguration(expectList)

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

        val potatoList = potatoSingle.withPath("/create-user-xml-list").withConfiguration(expectList)


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
                    .addConfiguration(failMapping)
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                //default is ignore
                potato,
                potato
                    .addConfiguration(ignoreMapping)
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
                    .addConfiguration(strictNullCheck),
                //default is strict
                fullObjectPotato,
                fullObjectPotato
                    .addConfiguration(strictNullCheck)
            )
        }

        shouldNotThrow<DeserializationFailureException> {
            baseCannon.fire(
                partialObjectPotato
                    .addConfiguration(relaxNullCheck),
                fullObjectPotato
                    .addConfiguration(relaxNullCheck)
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
                    .addConfiguration(CaseInsensitiveProperties)
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
                    .addConfiguration(CaseInsensitiveEnums)
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
                    .addConfiguration(AcceptEmptyStringAsNullObject)
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
            .withConfiguration(typeXmlHeader, checkXml)

        val potatoWithUnknownValueJson = potatoJson
            .withPath("/empty-enum-and-not-matched")

        val potatoWithUnknownValuesXml = potatoXml
            .withPath("/empty-enum-and-not-matched")
            .withConfiguration(typeXmlHeader, checkXml)

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
                .addConfiguration(UnknownEnumAsDefault)
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

        val potatoList = potatoSingle.withPath("/create-user-custom-list").withConfiguration(expectList)


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
                result.requestHeaders["Append-Header"]?.size shouldBe 2
            })

        val newPot = appendPotato
            .addExpectation(is200Expectation)

        val overwritePotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CustomHeader("Append-Header", "AppendValue"),
            CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.OVERWRITE),
            Expectation("Header Append Check -> contains 1 element") { result ->
                result.requestHeaders["Append-Header"] shouldContainExactly listOf("AppendValue2")
                result.requestHeaders["Append-Header"]?.size shouldBe 1
            })

        cannon.fire(newPot, overwritePotato)

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
            basePotato.withConfiguration(
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
            basePotato.withConfiguration(
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
            method = HttpMethod.POST, body = TextBody("{ }"), path = "/test", configuration = listOf(
                ContentType.JSON,
                ContentType.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                isHelloResponseExpectation,
                Expectation("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"]?.size shouldBe 1
                    result.requestHeaders["Content-Type"]?.first() shouldBe "application/xml"
                },
                Expectation("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"]?.size shouldBe 1
                    result.requestHeaders["Authorization"]?.first() shouldBe "Bearer mytoken"
                })
        )

        val cannon = baseCannon.addConfiguration(
            listOf(
                BasicAuth(
                    username = "user", password = "password"
                ), is200Expectation, QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
    }


    @Test
    fun `POST with alternate base path is sending the request to a different host`() {
        val randomLetters = (1..30).map { ('a'..'z').random() }.joinToString("")

        val alternateBeeceptorUrl = "https://$randomLetters.free.beeceptor.com"

        val verifyBeceptorUrl = Expectation("Beeceptor URL is used") { result ->
            result.fullUrl shouldContain "$alternateBeeceptorUrl/test"
        }

        val verifyNotLocalHost = Expectation("Potato is not fired towards localhost") { result ->
            result.fullUrl shouldNotContain "localhost"
        }

        val defaultPotato = Potato(
            method = HttpMethod.POST, path = "/test", is200Expectation, isHelloResponseExpectation
        )

        val overrideBaseUrlPotato = defaultPotato.withConfiguration(
            OverrideBaseUrl(alternateBeeceptorUrl), verifyBeceptorUrl, verifyNotLocalHost
        )

        baseCannon.fire(defaultPotato, overrideBaseUrlPotato)
    }

    @Test
    fun `POST with multiple headers to mockserver will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST, body = TextBody("{ }"), path = "/test", configuration = listOf(
                ContentType.JSON,
                ContentType.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                Expectation("Returns default beeceptor nothing configured yet message") { result ->
                    result.responseText() shouldBe "Hey ya! Great to see you here. BTW, nothing is configured here. Create a mock server on Beeceptor.com"
                },
                Expectation("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"]?.size shouldBe 1
                    result.requestHeaders["Content-Type"]?.first() shouldBe "application/xml"
                },
                Expectation("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"]?.size shouldBe 1
                    result.requestHeaders["Authorization"]?.first() shouldBe "Bearer mytoken"
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
            is404Expectation,
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

        baseCannon.fire(potato.addConfiguration(expect))
    }

    @Test
    fun `POST with some return values`() {
        val potato = Potato(
            method = HttpMethod.POST, path = "/create-user",

            ContentType.JSON, is200Expectation
        )

        baseCannon.fire(potato)
    }

    @Test
    fun `POST with all kinds of methods`() {
        val potatoes = HttpMethod.values().map {
            Potato(
                method = it, path = "/not-available-endpoint", body = TextBody("{ }"), configuration = listOf(
                    is404Expectation
                )
            )
        }.toList()

        baseCannon.fire(potatoes)

    }

    @Test
    fun `POST calls can be chained`() {
        val firstPotato = Potato(
            method = HttpMethod.POST, path = "/first-call", is200Expectation
        )

        val result = baseCannon.fire(firstPotato)

        baseCannon.fire(
            firstPotato.withPath("/second-call").addConfiguration(
                QueryParam("number", result[0].responseText() ?: "0")
            )
        )

    }

    @Test
    fun `Binary Body can be sent`() {
        val binaryContent = ByteArray(1024) { it.toByte() } // 1 KB of binary data

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = BinaryBody(binaryContent),
            ContentType.OCTET_STREAM,
            is200Expectation,
            isHelloResponseExpectation
        )


        baseCannon.fire(potato)
    }


}
