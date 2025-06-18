package io.github.boomkartoffel.potatocannon

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay

object TestBackend {
    private var server: ApplicationEngine? = null

    fun start(port: Int) {
        server = embeddedServer(Netty, port = port) {
            routing {
                get("/test") {
                    call.respondText("Hello")
                }
                get("/test-wait") {
                    delay(500)
                    println("Waiting for 500ms")
                    call.respondText("Hello")
                }
                post("/test") {
                    call.respondText("Hello")
                }

                post("/create-user") {
                    call.respondText("1,Max Muser,max@muster.com")
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}