package io.github.boomkartoffel.potatocannon

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

object TestBackend {
    private var server: ApplicationEngine? = null

    private var lastNumber: Int? = null


    fun start(port: Int) {

        server = embeddedServer(Netty, port = port) {
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
                    call.respondText("1,Max Muser,max@muster.com")
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
                        call.respondText("Mismatch or missing number, the true one is $lastNumber", status = HttpStatusCode.BadRequest)
                    }
                    lastNumber = null
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        lastNumber = null
    }
}