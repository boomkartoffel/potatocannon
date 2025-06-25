package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.strategy.BasicAuth
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.potato.BinaryBody
import io.github.boomkartoffel.potatocannon.strategy.ContentHeader
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.result.ListDeserializer
import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.result.DeserializationFormat
import io.github.boomkartoffel.potatocannon.result.SingleDeserializer
import io.github.boomkartoffel.potatocannon.strategy.OverrideBaseUrl
import io.github.boomkartoffel.potatocannon.strategy.BearerAuth
import io.github.boomkartoffel.potatocannon.strategy.CookieHeader
import io.github.boomkartoffel.potatocannon.strategy.CustomHeader
import io.github.boomkartoffel.potatocannon.strategy.HeaderUpdateStrategy
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.*
import kotlin.collections.first
import kotlin.properties.Delegates
import kotlin.random.Random

data class CreateUser(
    val id: Int,
    val name: String,
    val email: String
)

object CommaSeparatedSingleDeserializer : SingleDeserializer {
    override fun <T> deserializeSingle(data: String, targetClass: Class<T>): T {
        require(targetClass == CreateUser::class.java) {
            "Unsupported class for CommaSeparatedSingleDeserializer: $targetClass"
        }

        val parts = data.trim().split(",")
        if (parts.size != 3) error("Expected 3 fields for CreateUser")

        return CreateUser(
            id = parts[0].trim().toInt(),
            name = parts[1].trim(),
            email = parts[2].trim()
        ) as T
    }
}

object CommaSeparatedListDeserializer : ListDeserializer {
    override fun <T> deserializeList(data: String, targetClass: Class<T>): List<T> {
        require(targetClass == CreateUser::class.java) {
            "Unsupported class for CommaSeparatedListDeserializer: $targetClass"
        }

        return data.trim().split(";")
            .filter { it.isNotBlank() }
            .map { line ->
                CommaSeparatedSingleDeserializer.deserializeSingle(line, CreateUser::class.java) as T
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
            baseUrl = "http://localhost:$port",
        )
    }

    @AfterAll
    fun tearDown() {
        TestBackend.stop()
    }


    private val is200: Expectation = Expectation { result: Result ->
        Assertions.assertEquals(200, result.statusCode)
    }

    private val is200Verification = ResultVerification("is 200", is200)

    private val isHelloResponse: Expectation = Expectation { result: Result ->
        Assertions.assertEquals("Hello", result.responseText())
    }

    private val isHelloResponseVerification = isHelloResponse.withDescription("response is Hello")

    private val is404: Expectation = Expectation { result: Result ->
        Assertions.assertEquals(404, result.statusCode)
    }

    private val is404Verification = ResultVerification("is 404", is404)

    @Test
    fun `GET request to test returns Hello`() {

        val expect = Expectation { result: Result ->
            Assertions.assertEquals(result.statusCode, 200)
            Assertions.assertEquals("Hello", result.responseText())
        }

        val potato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            expect.withDescription("is 200 and response is Hello")
        )


        val cannon = baseCannon
            .withAmendedConfiguration(FireMode.SEQUENTIAL)

        cannon.fire(potato)
    }

    @Test
    fun `GET request times 10 to test-wait takes at least 5 seconds in sequential mode`() {
        val potatoes = (1..10).map {
            Potato(
                method = HttpMethod.GET,
                path = "/test-wait",
                is200Verification,
                isHelloResponseVerification

            )
        }

        val start = System.currentTimeMillis()
        baseCannon
            .withAmendedConfiguration(FireMode.SEQUENTIAL)
            .fire(potatoes)
        val end = System.currentTimeMillis()
        val durationMs = end - start

        println("Sequential duration: $durationMs ms")
        Assertions.assertTrue(durationMs >= 5000, "Expected at least 5 seconds")
    }

    @Test
    fun `GET request times 500 to test-wait takes less than 1 second in parallel mode`() {
        val potatoes = (1..500).map {
            Potato(
                method = HttpMethod.GET,
                path = "/test-wait-parallel",
                is200Verification,
                isHelloResponseVerification
            )
        }


        val start = System.currentTimeMillis()
        baseCannon.fire(potatoes)
        val end = System.currentTimeMillis()
        val durationMs = end - start

        println("Parallel duration: $durationMs ms")
        Assertions.assertTrue(durationMs < 1100, "Expected under 1.1 second")
    }

    @Test
    fun `POST request with some body is printed`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextBody("{ }"),
            ContentHeader.JSON,
            is200Verification,
            isHelloResponseVerification
        )


        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
        )

        cannon.fire(potato)
    }

    @Test
    fun `POST request to create user returns serializable JSON`() {
        val expect = Expectation { result: Result ->
            val created = result.bodyAsSingle(CreateUser::class.java)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single JSON object")

        val expectList = Expectation { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java)
            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from JSON List")

        val potatoSingleObject = Potato(
            method = HttpMethod.POST,
            path = "/create-user",
            expect
        )

        val potatoList = potatoSingleObject
            .withPath("/create-user-list")
            .withConfiguration(expectList)

        baseCannon.fire(potatoSingleObject, potatoList)
    }

    @Test
    fun `POST request to create user returns serializable XML`() {
        val expectSingle = Expectation { result: Result ->
            val created = result.bodyAsSingle(CreateUser::class.java, DeserializationFormat.XML)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML")

        val expectList = Expectation { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java, DeserializationFormat.XML)

            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from XML as List")

        val potatoSingle = Potato(
            method = HttpMethod.POST,
            path = "/create-user-xml",
            expectSingle
        )

        val potatoList = potatoSingle
            .withPath("/create-user-xml-list")
            .withConfiguration(expectList)


        baseCannon.fire(potatoSingle, potatoList)

    }

    @Test
    fun `POST request to create user returns serializable XML and can be deserialized with a different charset`() {
        val expectSingle = Expectation { result: Result ->
            val created = result.bodyAsSingle(CreateUser::class.java, DeserializationFormat.XML, Charsets.UTF_32)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single XML with a UTF32 charset")

        val potatoSingle = Potato(
            method = HttpMethod.POST,
            path = "/create-user-xml-utf32",
            expectSingle
        )

        baseCannon.fire(potatoSingle)
    }

    @Test
    fun `POST request to create user with custom formatting can be serialized with custom mapper`() {
        val expectSingle = Expectation { result: Result ->
            val created = result.bodyAsSingle(CreateUser::class.java, CommaSeparatedSingleDeserializer)
            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from single csv line")

        val expectList = Expectation { result: Result ->
            val createdList = result.bodyAsList(CreateUser::class.java, CommaSeparatedListDeserializer)

            createdList.size shouldBe 1

            val created = createdList.first()

            created.id shouldBe 1
            created.name shouldBe "Max Muster"
            created.email shouldBe "max@muster.com"
        }.withDescription("Response is correctly deserialized from csv as List")

        val potatoSingle = Potato(
            method = HttpMethod.POST,
            path = "/create-user-custom",
            expectSingle
        )

        val potatoList = potatoSingle
            .withPath("/create-user-custom-list")
            .withConfiguration(expectList)


        baseCannon.fire(potatoSingle, potatoList)

    }

    @Test
    fun `POST requests with Header Strategy is working correctly`() {
        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            FireMode.PARALLEL
        )

        val appendPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CustomHeader("Append-Header", "AppendValue"),
            CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.APPEND),
            ResultVerification("Header Append Check -> contains 2 elements") { result ->
                result.requestHeaders["Append-Header"] shouldContainExactly listOf("AppendValue", "AppendValue2")
                result.requestHeaders["Append-Header"]?.size shouldBe 2
            }
        )

        val overwritePotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CustomHeader("Append-Header", "AppendValue"),
            CustomHeader("Append-Header", "AppendValue2", HeaderUpdateStrategy.OVERWRITE),
            ResultVerification("Header Append Check -> contains 1 element") { result ->
                result.requestHeaders["Append-Header"] shouldContainExactly listOf("AppendValue2")
                result.requestHeaders["Append-Header"]?.size shouldBe 1
            }
        )

        cannon.fire(appendPotato, overwritePotato)

    }


    @Test
    fun `POST requests have correct logging`() {
        val headers = listOf(
            ContentHeader.JSON,
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

        val baseLoggingPotatoes = Logging.entries
            .map { logging ->
                basePotato.withConfiguration(
                    headers + logging + ResultVerification(
                        {
                            println("⬆\uFE0F This was logging: $logging")
                        }
                    ))
            }

        val logExcludeCombinations = buildSet<Set<LogExclude>> {
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
                headers + Logging.FULL + logExcludes + ResultVerification(
                    {
                        println("⬆\uFE0F This was FULL logging with excludes: $logExcludes")
                    })
            )
        }

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            FireMode.SEQUENTIAL,
        )

        cannon.fire(baseLoggingPotatoes + baseLoggingPotatoesWithExcludes)
    }


    @Test
    fun `POST with multiple headers will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST,
            body = TextBody("{ }"),
            path = "/test",
            configuration = listOf(
                ContentHeader.JSON,
                ContentHeader.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                isHelloResponseVerification,
                ResultVerification("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"]?.size shouldBe 1
                    result.requestHeaders["Content-Type"]?.first() shouldBe "application/xml"
                },
                ResultVerification("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"]?.size shouldBe 1
                    result.requestHeaders["Authorization"]?.first() shouldBe "Bearer mytoken"
                }
            )
        )

        val cannon = baseCannon.withAmendedConfiguration(
            listOf(
                BasicAuth(
                    username = "user",
                    password = "password"
                ),
                is200Verification,
                QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
    }


    @Test
    fun `POST with alternate base path is sending the request to a different host`() {
        val randomLetters = (1..30)
            .map { ('a'..'z').random() }
            .joinToString("")

        val alternateBeeceptorUrl = "https://$randomLetters.free.beeceptor.com"

        val verifyBeceptorUrl = ResultVerification("Beeceptor URL is used") { result ->
            result.fullUrl shouldContain "$alternateBeeceptorUrl/test"
        }

        val verifyNotLocalHost = ResultVerification("Potato is not fired towards localhost") { result ->
            result.fullUrl shouldNotContain "localhost"
        }

        val defaultPotato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            is200Verification,
            isHelloResponseVerification
        )

        val overrideBaseUrlPotato = defaultPotato
            .withConfiguration(
                OverrideBaseUrl(alternateBeeceptorUrl),
                verifyBeceptorUrl,
                verifyNotLocalHost
            )

        baseCannon.fire(defaultPotato, overrideBaseUrlPotato)
    }

    @Test
    fun `POST with multiple headers to mockserver will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST,
            body = TextBody("{ }"),
            path = "/test",
            configuration = listOf(
                ContentHeader.JSON,
                ContentHeader.XML,
                QueryParam("queryPotato", "valuePotato"),
                QueryParam("queryPotato", "valuePotato2"),
                BearerAuth("mytoken"),
                ResultVerification("Returns default beeceptor nothing configured yet message") { result ->
                    result.responseText() shouldBe "Hey ya! Great to see you here. BTW, nothing is configured here. Create a mock server on Beeceptor.com"
                },
                ResultVerification("Only one content type is provided and that is XML") { result ->
                    result.requestHeaders["Content-Type"]?.size shouldBe 1
                    result.requestHeaders["Content-Type"]?.first() shouldBe "application/xml"
                },
                ResultVerification("Only one Auth Header type is provided and that is the Bearer token") { result ->
                    result.requestHeaders["Authorization"]?.size shouldBe 1
                    result.requestHeaders["Authorization"]?.first() shouldBe "Bearer mytoken"
                }
            )
        )

        val randomLetters = (1..30)
            .map { ('a'..'z').random() }
            .joinToString("")

        val randomBeeceptorUrl = "$randomLetters.free.beeceptor.com"

        val beeceptorCannon = Cannon(
            baseUrl = "https://$randomBeeceptorUrl",
            BasicAuth(
                username = "user",
                password = "password"
            ),
            CustomHeader("X-Custom-Header", "CustomValue"),
            is404Verification,
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

        val expect = ResultVerification("Characters are URL encoded") { result ->
            result.fullUrl shouldContain "test?+++++++++++query=value+with+spaces+%26+special+characters+like+%3F+and+%3D&query2=value2+with+%3C+or+%C3%9C%C3%96%C3%84%3E+special+characters"
        }

        baseCannon.fire(potato.withAmendedConfiguration(expect))
    }

    @Test
    fun `POST with some return values`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/create-user",

            ContentHeader.JSON,
            is200Verification
        )

        baseCannon.fire(potato)
    }

    @Test
    fun `POST with all kinds of methods`() {
        val potatoes = HttpMethod.entries.map {
            Potato(
                method = it,
                path = "/not-available-endpoint",
                body = TextBody("{ }"),
                configuration = listOf(
                    ResultVerification(is404)
                )
            )
        }.toList()

        baseCannon.fire(potatoes)

    }

    @Test
    fun `POST calls can be chained`() {
        val firstPotato = Potato(
            method = HttpMethod.POST,
            path = "/first-call",
            is200Verification
        )

        val result = baseCannon.fireOne(firstPotato)

        baseCannon.fire(
            firstPotato
                .withPath("/second-call")
                .withAmendedConfiguration(
                    QueryParam("number", result.responseText() ?: "0")
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
            ContentHeader.OCTET_STREAM,
            is200Verification,
            isHelloResponseVerification
        )


        baseCannon.fire(potato)
    }


}
