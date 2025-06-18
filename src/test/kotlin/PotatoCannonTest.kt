package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.strategy.BasicAuth
import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.strategy.ContentHeader
import io.github.boomkartoffel.potatocannon.strategy.ContentType
import io.github.boomkartoffel.potatocannon.strategy.FireMode
import io.github.boomkartoffel.potatocannon.cannon.Mode
import io.github.boomkartoffel.potatocannon.potato.Expectation
import io.github.boomkartoffel.potatocannon.potato.HttpMethod
import io.github.boomkartoffel.potatocannon.potato.Potato
import io.github.boomkartoffel.potatocannon.potato.TextBody
import io.github.boomkartoffel.potatocannon.strategy.BearerAuth
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
            configuration = listOf(
                ResultVerification(expect)
            )
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
                configuration = listOf(
                    ResultVerification(is200),
                    ResultVerification { result ->
                        Assertions.assertEquals("Hello", result.responseText())
                    }
                )
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
                path = "/test-wait",
                configuration = listOf(
                    ResultVerification(is200),
                    ResultVerification { result ->
                        Assertions.assertEquals("Hello", result.responseText())
                    }
                )
            )
        }

        val cannon = Cannon(
            baseUrl = "http://localhost:$port",
            configuration = listOf(
                FireMode(Mode.Parallel),
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
            body = TextBody("{ }"),
            path = "/test",
            configuration = mutableListOf(
                ContentHeader(ContentType.JSON),
                ResultVerification { result ->
                    Assertions.assertEquals(200, result.statusCode)
                    Assertions.assertEquals("Hello", result.responseText())
                }
            )
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
    fun `POST with multiple headers will have later ones overwrite earlier`() {
        val potato = Potato(
            method = HttpMethod.POST,
            body = TextBody("{ }"),
            path = "/test",
            configuration = listOf(
                ContentHeader(ContentType.JSON),
                ContentHeader(ContentType.XML),
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
                ContentHeader(ContentType.JSON),
                ContentHeader(ContentType.XML),
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
            configuration = listOf(
                ContentHeader(ContentType.JSON),
                ResultVerification(is200)

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


}
