package io.github.boomkartoffel.potatocannon

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.netty.channel.ChannelHandlerContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.*
import java.util.logging.LogManager
import kotlin.coroutines.CoroutineContext


data class TimeOutConfig(
    val id: String,
    val returnOkAfterAttempt: Int,
    val currentAttempt: Int = 1,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TimeOutConfig && this.id == other.id)

    override fun hashCode(): Int = id.hashCode()
}


@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String
)

object TestBackend {
    private lateinit var server: ApplicationEngine

    private var lastNumber: Int? = null

    private val timeoutConfigs: MutableSet<TimeOutConfig> = Collections.synchronizedSet(mutableSetOf())

    init {
        // Remove existing handlers from JUL root logger -> this is to remove error logging from netty on shutdown and have the logback-test.xml apply
        LogManager.getLogManager().reset()
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
    }


    fun start(port: Int) {

        server = embeddedServer(
            Netty,
            port = port,
            configure = {
                // Allow many concurrent calls
                runningLimit = 2_000
                // Optional: let the queue absorb bursts
                requestQueueLimit = 10_000
            }
        ) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/no-body") {
                    call.respond(HttpStatusCode.OK)
                }

                get("/test") {
                    call.respondText("Hello")
                }
                get("/test-wait") {
                    delay(500)
                    call.respondText("Hello")
                }
                get("/test-wait-parallel") {
                    delay(500)
                    call.respondText("Hello")
                }
                post("/test") {
                    call.respondText("Hello")
                }
                post("/test-logging") {
                    call.response.cookies.append(
                        Cookie(
                            name = "session",
                            value = "abc123",
                            path = "/",
                            httpOnly = true
                        )
                    )
                    call.respondText("Hello")
                }

                post("/create-user") {
                    val user = User(1, "Max Muster", "max@muster.com")
                    call.respond(user)
                }

                post("/create-user-empty-string") {
                    val json = """
                            {
                                "user": ""
                            }
                            """.trimIndent()
                    call.respond(json)
                }

                post("/empty-enum") {
                    val type = call.request.queryParameters["type"]
                    if (type == "xml") {
                        val xml = """
                            <EmptyEnumCheckObject>
                                <enum></enum>
                                <enum2></enum2>
                            </EmptyEnumCheckObject>
                        """.trimIndent()
                        call.respondText(xml, ContentType.Application.Xml)
                    } else {
                        val json = """
                            {
                                "enum": "",
                                "enum2": ""
                            }
                        """.trimIndent()
                        call.respondText(json, ContentType.Application.Json)
                    }
                }

                post("/empty-enum-and-not-matched") {
                    val type = call.request.queryParameters["type"]
                    if (type == "xml") {
                        val xml = """
                            <EmptyEnumCheckObject>
                                <enum></enum>
                                <enum2>UNKNOWN</enum2>
                            </EmptyEnumCheckObject>
                        """.trimIndent()
                        call.respondText(xml, ContentType.Application.Xml)
                    } else {
                        val json = """
                            {
                                "enum": "",
                                "enum2": "UNKNOWN"
                            }
                        """.trimIndent()
                        call.respondText(json, ContentType.Application.Json)
                    }
                }

                post("/case-insensitive-enum") {
                    val json = """
                            {
                                "enum": "NoNe",
                                "enum2": "some"
                            }
                        """.trimIndent()
                    call.respondText(json, ContentType.Application.Json)

                }


                post("/create-user-list") {
                    val user = User(1, "Max Muster", "max@muster.com")
                    call.respond(listOf(user))
                }

                post("/create-user-case-different") {
                    val json = """
                        [
                            {
                                "iD": 1,
                                "NAME": "Max Muster",
                                "eMail": "max@muster.com"
                            },
                            {
                                "Id": 1,
                                "namE": "Max Muster",
                                "Email": "max@muster.com"
                            },
                            {
                                "ID": 1,
                                "NAME": "Max Muster",
                                "EMAIL": "max@muster.com"
                            }
                        ]
                            """.trimIndent()
                    call.respond(json)
                }

                post("/create-user-xml") {
                    val xml = """
                                <User>
                                    <id>1</id>
                                    <name>Max Muster</name>
                                    <email>max@muster.com</email>
                                </User>
                            """.trimIndent()
                    call.respond(xml)
                }

                post("/create-user-json-unknown-field") {
                    val json = """
                                {
                                    "id": 1,
                                    "name": "Max Muster",
                                    "email": "max@muster.com",
                                    "unknownField": "This field is not defined in the User class"
                                }
                            """.trimIndent()
                    call.respond(json)
                }

                get("/null-check-object-partial") {
                    val json = """
                                {
                                    "map": null,
                                    "list": null
                                }
                            """.trimIndent()
                    call.respond(json)
                }

                get("/null-check-object-full") {
                    val json = """
                                {
                                    "map": null,
                                    "list": null,
                                    "string": null,
                                    "int": null
                                }
                            """.trimIndent()
                    call.respond(json)
                }

                get("/time-object") {
                    val json = """
                                {
                                    "localDate": "2025-08-20",
                                    "localTime": "10:15:30",
                                    "localDateTime": "2025-08-20T10:15:30",
                                    "zonedDateTime": "2025-08-20T10:15:30+02:00[Europe/Berlin]",
                                    "offsetTime": "10:15:30+02:00",
                                    "offsetDateTime": "2025-08-20T10:15:30+02:00",
                                    "instant": "2025-08-20T08:15:30Z",
                                    "year": "2025",
                                    "yearMonth": "2025-08",
                                    "monthDay": "--08-20",
                                    "duration": "PT2H30M",
                                    "period": "P1Y2M3D",
                                    "zoneId": "Europe/Berlin",
                                    "zoneOffset": "+02:00"
                                }
                            """.trimIndent()
                    call.respond(json)
                }

                post("/create-user-xml-utf32") {
                    val xml = """
                                <User>
                                    <id>1</id>
                                    <name>Max Muster</name>
                                    <email>max@muster.com</email>
                                </User>
                            """.trimIndent()

                    call.respondText(
                        text = xml,
                        contentType = ContentType.Text.Xml.withCharset(Charsets.UTF_32)
                    )
                }

                post("/create-user-xml-list") {
                    val xml = """
                                <Users>
                                    <User>
                                        <id>1</id>
                                        <name>Max Muster</name>
                                        <email>max@muster.com</email>
                                    </User>
                                </Users>
                            """.trimIndent()
                    call.respond(xml)
                }

                post("/xml-data-with-attributes") {
                    val xml = """
                        <?pc generated="true"?>
                        <!-- A comment before the root -->
                        <Users
                            xmlns="urn:pc:users"
                            xmlns:m="urn:pc:meta"
                            xml:lang="en"
                            m:version="1.2"
                            id="U-集合">
                          
                          <User id="1" role="admin" active="true" m:tags="alpha,beta" created-at="2025-09-22T11:22:33Z">
                            <name nick="Maxi">Max Muster</name>
                            <email verified="yes">max@muster.com</email>
                
                            <aliases>
                              <alias primary="true"/>
                              <alias>Max M.</alias>
                            </aliases>
                
                            <address type="home">
                              <line1>Hauptstraße 1</line1>
                              <city>München</city>
                              <country code="DE">Deutschland</country>
                            </address>
                
                            <!-- Mixed whitespace handling -->
                            <bio xml:space="preserve">Line 1
                Line 2    with   spaces</bio>
                
                            <!-- CDATA with angle brackets and ampersands -->
                            <notes><![CDATA[
                Some free-form text with <tags> & weird stuff like 1 < 2 and URLs http://example.com/?a=1&b=2
                            ]]></notes>
                
                            <!-- Base64-ish blob -->
                            <avatar media-type="image/png" encoding="base64">iVBORw0KGgoAAAANSUhEUgAAAAUA...</avatar>
                
                            <!-- Namespaced metadata -->
                            <m:extra>
                              <m:item key="🚀">rocket</m:item>
                            </m:extra>
                          </User>
                
                          <User id="2" role="user" active="false">
                            <name>Jane Doe</name>
                            <email>jane.doe@example.com</email>
                
                            <!-- Empty and self-closing forms -->
                            <empty-element />
                            <self-closing/>
                
                            <!-- Inline list -->
                            <numbers><n>0</n><n>1</n><n>2</n></numbers>
                
                            <!-- Mixed content -->
                            Mixed content before <inline-elem attr="v">X</inline-elem> after.
                          </User>
                        </Users>
                    """.trimIndent()
                    call.respond(xml)
                }

                post("/create-user-custom") {
                    val xml = """
                                1,Max Muster,max@muster.com 
                            """.trimIndent()
                    call.respond(xml)
                }

                post("/create-user-custom-list") {
                    val xml = """
                                1,Max Muster,max@muster.com;
                            """.trimIndent()
                    call.respond(xml)
                }

                post("/first-call") {
                    val randomNumber = (1000..9999).random()
                    lastNumber = randomNumber
                    call.respondText(randomNumber.toString())
                }

                post("/second-call") {
                    val receivedNumber = call.request.queryParameters["number"]?.toIntOrNull()
                    if (receivedNumber != null && receivedNumber == lastNumber) {
                        call.respondText("OK", status = HttpStatusCode.OK)
                    } else {
                        call.respondText(
                            "Mismatch or missing number, the true one is $lastNumber",
                            status = HttpStatusCode.BadRequest
                        )
                    }
                    lastNumber = null
                }

                post("/t") {
                    val ms = call.request.queryParameters["ms"]?.toLongOrNull() ?: 0L
                    delay(ms)
                    call.respondText("ok")
                }

                post("/mirror") {
                    call.respondText(call.receiveText())
                }

                post("/timeout") {

                    val id = call.request.queryParameters["id"] ?: ""
                    val returnOkAfter = call.request.queryParameters["returnOkAfter"]?.toIntOrNull() ?: 1

                    if (id.isEmpty()) {
                        awaitCancellation()
                    }

                    val currentConfig = timeoutConfigs.find { it.id == id } ?: TimeOutConfig(id, returnOkAfter)

                    if (currentConfig.currentAttempt >= returnOkAfter) {
                        call.respondText("OK", status = HttpStatusCode.OK)
                    } else {
                        val newConfig =
                            currentConfig.copy(currentAttempt = currentConfig.currentAttempt + 1)
                        timeoutConfigs.remove(newConfig)
                        timeoutConfigs.add(newConfig)

                        awaitCancellation()
                    }

                }
            }
        }

        server.start(wait = false)
    }


    fun stop() {
        server.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 5000
        )
        lastNumber = null
    }
}


private fun closeFromCoroutineContext(ctx: CoroutineContext): Boolean {
    // Find Ktor's Netty context element
    val nettyElem = ctx.fold<Any?>(null) { acc, el ->
        acc ?: el.takeIf { it::class.java.name.endsWith("NettyDispatcher\$CurrentContext") }
    } ?: return false

    // It has a private field named "context" of type ChannelHandlerContext
    val field = nettyElem::class.java.getDeclaredField("context").apply { isAccessible = true }
    val chCtx = field.get(nettyElem) as ChannelHandlerContext

    // Either of these works:
    // chCtx.close()                // closes via the context (ChannelOutboundvoker)
    chCtx.channel().close()         // closes the underlying channel immediately
    return true
}
