package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.cannon.Cannon
import io.github.boomkartoffel.potatocannon.exception.ContextFailureException
import io.github.boomkartoffel.potatocannon.exception.RequestPreparationException
import io.github.boomkartoffel.potatocannon.potato.*
import io.github.boomkartoffel.potatocannon.strategy.*
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeTypeOf
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.properties.Delegates
import kotlin.random.Random

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContextTest {

    var port by Delegates.notNull<Int>()

    var baseCannon by Delegates.notNull<Cannon>()

    var globalContextCannon by Delegates.notNull<Cannon>()

    @BeforeAll
    fun setUp() {
        port = Random.nextInt(30_000, 60_000)
        TestBackend.start(port)
        baseCannon = Cannon(
            baseUrl = "http://127.0.0.1:$port",
        )
        globalContextCannon = baseCannon
            .withGlobalContext(PotatoCannonContext().apply { this["globalValue"] = "1" })
    }

    @AfterAll
    fun tearDown() {
        TestBackend.stop()
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
    fun `Potatoes can be constructed from the context`() {
        val ctx = PotatoCannonContext().apply { this["path"] = "/test" }
        val expectPostMethod = Expectation("Method is POST") { result ->
            result.potato.method shouldBe HttpMethod.POST
        }
        val potatoFromContext = PotatoFromContext.single {
            Potato(
                method = HttpMethod.POST,
                path = it["path"] ?: "/not-set",
                expect200StatusCode,
                expectHelloResponseText,
                expectPostMethod
            )
        }

        val multiplePotatoesFromContext = PotatoFromContext.many {
            listOf(
                Potato.post(
                    path = it["path"] ?: "/not-set",
                    expect200StatusCode,
                    expectHelloResponseText,
                    expectPostMethod,
                    QueryParam("call", "1")
                ),
                Potato.post(
                    path = it["path"] ?: "/not-set",
                    expect200StatusCode,
                    expectHelloResponseText,
                    expectPostMethod,
                    QueryParam("call", "2")
                ),
            )
        }

        val regularPotato = Potato(
            method = HttpMethod.GET,
            path = "/test",
            expect200StatusCode
        )


        baseCannon
            .withSessionContext(ctx)
            .withFireMode(FireMode.SEQUENTIAL)
            .fire(potatoFromContext, multiplePotatoesFromContext)
            .fire(regularPotato)
            .fire(potatoFromContext, regularPotato)
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
    fun `A value can only be stored into the global context if it has been defined`() {

        val potato = Potato(
            method = HttpMethod.POST,
            path = "/test",
            CaptureToContext.global("key") { r ->
                r.responseText()
            }
        )

        shouldThrow<ContextFailureException> {
            baseCannon
                .fire(potato)
        }.message shouldBe "Cannot write the key 'key' with the value of type class java.lang.String to the global context if it has not been created. Add a global context to the cannon settings first or use the session context."
    }


    @Test
    fun `Getting a value of the wrong type or a value that is missing in session and global context returns null`() {
        val potato = Potato.post(
            path = "/mirror",
            BodyFromContext {
                val missingValue = it.get<String>("missing")
                val wrongType = it.get<String>("wrongType")
                TextPotatoBody("missing: $missingValue, wrongType: $wrongType")
            }, Check {
                it.responseText() shouldBe "missing: null, wrongType: null"
            }
        )


        baseCannon
            .withSessionContext(PotatoCannonContext().apply { this["wrongType"] = 12 })
            .fire(potato)
    }


    val keySessionFunction1 = "key-session-function-1"
    val keySessionFunction2 = "key-session-function-2"

    @Test
    fun `Testfunction can access global context but not session context from another function - 1`() {
        val sessionContext = PotatoCannonContext().apply { this[keySessionFunction1] = "2" }
        val potato = Potato
            .post(
                "/mirror", BodyFromContext {
                    val globalVal = it["globalValue"] ?: ""
                    val sessionValue2 = it[keySessionFunction1] ?: ""
                    val sessionValue3 = it[keySessionFunction2] ?: ""
                    TextPotatoBody("$globalVal$sessionValue2$sessionValue3")
                },
                Check {
                    it.responseText() shouldBe "12"
                })

        globalContextCannon
            .withSessionContext(sessionContext)
            .fire(potato)
    }

    @Test
    fun `Testfunction can access global context but not session context from another function - 2`() {
        val sessionContext = PotatoCannonContext().apply { this[keySessionFunction2] = "3" }
        val potato = Potato
            .post(
                "/mirror", BodyFromContext {
                    val globalVal = it["globalValue"] ?: ""
                    val sessionValue2 = it[keySessionFunction1] ?: ""
                    val sessionValue3 = it[keySessionFunction2] ?: ""
                    TextPotatoBody("$globalVal$sessionValue2$sessionValue3")
                },
                Check {
                    it.responseText() shouldBe "13"
                })

        globalContextCannon
            .withSessionContext(sessionContext)
            .fire(potato)
    }

}