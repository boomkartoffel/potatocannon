package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.matchers.be
import io.kotest.matchers.or
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.startWith
import java.net.URI


val expect200StatusCode = Expectation("Response Code is 200 OK") { result ->
    result.statusCode shouldBe 200
}

val expectHelloResponseText = Check { result ->
    result.responseText() shouldBe "Hello"
}.withDescription("Response is Hello")

val expectOKResponseText = Check { result ->
    result.responseText() shouldBe "OK"
}.withDescription("Response is OK")


val expect400StatusCode = Check { result: Result ->
    result.statusCode shouldBe 404
}.withDescription("Response Code is 404 Not Found")

val expect4Attempts = Check {
    it.attempts shouldBe 4
}.withDescription("Request succeeded after 3 retries (4 attempts total)")


val expect12Attempts = Check {
    it.attempts shouldBe 12
}.withDescription("Request succeeded after 11 retries (12 attempts total)")


val expectResponseBodyIsMissing = Check {
    it.responseText() shouldBe ""
}.withDescription("Response body is missing")

val expectHostNotToBeLocalhost = Expectation(
    "Potato is not fired towards localhost") {
    it.fullUrl shouldNotContain "localhost"
    it.fullUrl shouldNotContain "127.0.0.1"
}

val expectHostToBeLocalhost = Expectation("Potato is fired towards localhost") {
    val host = URI(it.fullUrl).host?.lowercase() ?: ""
    host should (be("localhost") or be("::1") or startWith("127."))
}