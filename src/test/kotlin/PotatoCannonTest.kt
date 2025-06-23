package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.strategy.BasicAuth
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.strategy.ContentHeader
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.cannon.Mode
import io.github.boomkartoffel.potatocannon.potato.Expectation
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.BearerAuth
import io.github.boomkartoffel.potatocannon.strategy.CookieHeader
import io.github.boomkartoffel.potatocannon.strategy.CustomHeader
import io.github.boomkartoffel.potatocannon.strategy.LogExclude
import io.github.boomkartoffel.potatocannon.strategy.Logging
import io.github.boomkartoffel.potatocannon.strategy.QueryParam
import io.github.boomkartoffel.potatocannon.strategy.ResultVerification
import org.junit.jupiter.api.*
import kotlin.properties.Delegates
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PotatoCannonTest {

    var port by Delegates.notNull<Int>()

    @BeforeAll
    fun setUp() {
        port = Random.nextInt(30_000, 60_000)
        TestBackend.start(port)
    }

    @AfterAll
    fun tearDown() {
        TestBackend.stop()
    }

    private val is200: Expectation = Expectation { result: Result ->
        Assertions.assertEquals(200, result.statusCode)
    }

    private val is404: Expectation = Expectation { result: Result ->
        Assertions.assertEquals(404, result.statusCode)
    }

    @Test
    fun `GET request to test returns Hello`() {

        val expect = Expectation { result: Result ->
            Assertions.assertEquals(result.statusCode, 200)
            Assertions.assertEquals("Hello", result.responseText())
        }

        val potato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            ResultVerification(expect)
        )


        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Sequential),
            )
        )

        cannon.fire(potato)
    }

    @Test
    fun `GET request times 10 to test-wait takes at least 5 seconds in sequential mode`() {
        val potatoes = (1..10).map {
            Potato(
                method = HttpMethod.GET,
                path = "/test-wait",
                ResultVerification(is200),
                ResultVerification { result ->
                    Assertions.assertEquals("Hello", result.responseText())
                }

            )
        }

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Sequential),
                BasicAuth(
                    username = "user",
                    password = "password"
                )
            )
        )

        val start = System.nanoTime()
        cannon.fire(potatoes)
        val end = System.nanoTime()
        val durationMillis = (end - start) / 1_000_000

        println("Sequential duration: $durationMillis ms")
        Assertions.assertTrue(durationMillis >= 5000, "Expected at least 5 seconds")
    }

    @Test
    fun `GET request times 500 to test-wait takes less than 1 second in parallel mode`() {
        val potatoes = (1..500).map {
            Potato(
                method = HttpMethod.GET,
                path = "/test-wait-parallel",
                ResultVerification(is200),
                ResultVerification { result ->
                    Assertions.assertEquals("Hello", result.responseText())
                }

            )
        }

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
                Logging.OFF
            )
        )

        val start = System.nanoTime()
        cannon.fire(potatoes)
        val end = System.nanoTime()
        val durationMillis = (end - start) / 1_000_000

        println("Parallel duration: $durationMillis ms")
        Assertions.assertTrue(durationMillis < 1000, "Expected under 1 second")
    }

    @Test
    fun `POST request with some body is printed`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            body = TextBody("{ }"),
            ContentHeader.JSON,
            ResultVerification { result ->
                Assertions.assertEquals(200, result.statusCode)
                Assertions.assertEquals("Hello", result.responseText())
            }

        )


        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
            )
        )

        cannon.fire(potato)
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

        val logExcludeCombinations = setOf(
            listOf(),
            listOf(LogExclude.HEADERS),
            listOf(LogExclude.BODY),
            listOf(LogExclude.QUERY_PARAMS),
            listOf(LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.HEADERS, LogExclude.BODY),
            listOf(LogExclude.HEADERS, LogExclude.QUERY_PARAMS),
            listOf(LogExclude.HEADERS, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.BODY, LogExclude.QUERY_PARAMS),
            listOf(LogExclude.BODY, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.QUERY_PARAMS, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.HEADERS, LogExclude.BODY, LogExclude.QUERY_PARAMS),
            listOf(LogExclude.HEADERS, LogExclude.BODY, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.HEADERS, LogExclude.QUERY_PARAMS, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.BODY, LogExclude.QUERY_PARAMS, LogExclude.SECURITY_HEADERS),
            listOf(LogExclude.HEADERS, LogExclude.BODY, LogExclude.QUERY_PARAMS, LogExclude.SECURITY_HEADERS)
        )

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
            FireMode(Mode.Sequential),
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
                ResultVerification { result ->
                    Assertions.assertEquals("Hello", result.responseText())
                    Assertions.assertEquals(1, result.requestHeaders["Content-Type"]?.size)
                    Assertions.assertEquals("application/xml", result.requestHeaders["Content-Type"]?.first())
                    Assertions.assertEquals("Bearer mytoken", result.requestHeaders["Authorization"]?.first())
                    Assertions.assertEquals(1, result.requestHeaders["Authorization"]?.size)
                }
            )
        )


        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
                BasicAuth(
                    username = "user",
                    password = "password"
                ),
                ResultVerification(is200),
                QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
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
                ResultVerification { result ->
                    Assertions.assertEquals(
                        "Hey ya! Great to see you here. Btw, nothing is configured for this request path. Create a rule and start building a mock API.",
                        result.responseText()
                    )
                    Assertions.assertEquals(1, result.requestHeaders["Content-Type"]?.size)
                    Assertions.assertEquals("application/xml", result.requestHeaders["Content-Type"]?.first())
                    Assertions.assertEquals("Bearer mytoken", result.requestHeaders["Authorization"]?.first())
                    Assertions.assertEquals(1, result.requestHeaders["Authorization"]?.size)
                }
            )
        )


        val cannon = Cannon(
            baseUrl = "https://mytestxyxyxyx.free.beeceptor.com",
            configuration = listOf(
                FireMode(Mode.Parallel),
                BasicAuth(
                    username = "user",
                    password = "password"
                ),
                CustomHeader("X-Custom-Header", "CustomValue"),
                ResultVerification(is200),
                QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
    }

    @Test
    fun `POST with some return values`() {
        val potato = Potato(
            method = HttpMethod.POST,
            path = "/create-user",

            ContentHeader.JSON,
            ResultVerification(is200)


        )

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
                BasicAuth(
                    username = "user",
                    password = "password"
                ),
                ResultVerification(is200),
                QueryParam("queryCannon", "valueCannon")
            )
        )

        cannon.fire(potato)
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

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
            )
        )

        cannon.fire(potatoes)

    }

    @Test
    fun `POST calls can be chained`() {
        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
        )

        val firstPotato = Potato(
            method = HttpMethod.POST,
            path = "/first-call",
            ResultVerification(is200)
        )

        val results = cannon.fire(firstPotato)

        cannon.fire(
            firstPotato
                .withPath("/second-call")
                .withConfiguration(
                    QueryParam("number", results.first().responseText() ?: "0"),
                    ResultVerification(is200)
                )
        )

    }


}
