package io.github.boomkartoffel.potatocannon

import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import org.slf4j.bridge.SLF4JBridgeHandler
import java.util.logging.LogManager


@Serializable
data class User(
    val id: Int,
    val name: String,
    val email: String
)

object TestBackend {
    private var server: EmbeddedServer<*, *>? = null

    private var lastNumber: Int? = null

    init {
        // Remove existing handlers from JUL root logger -> this is to remove error logging from netty on shutdown and have the logback-test.xml apply
        LogManager.getLogManager().reset()
        SLF4JBridgeHandler.removeHandlersForRootLogger()
        SLF4JBridgeHandler.install()
    }


    fun start(port: Int) {

        server = embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/test") {
                    call.respondText("Hello")
                }
                get("/test-wait") {
                    delay(500)
                    println(System.currentTimeMillis())
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

                post("/create-user-list") {
                    val user = User(1, "Max Muster", "max@muster.com")
                    call.respond(listOf(user))
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
            }
        }

        server?.start(wait = false)
    }

    fun stop() {
        server?.stop(
            gracePeriodMillis = 1000,
            timeoutMillis = 5000
        )
        lastNumber = null
    }
}