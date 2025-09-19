package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.result.Result
import io.github.boomkartoffel.potatocannon.strategy.Check
import io.github.boomkartoffel.potatocannon.strategy.Expectation
import io.github.boomkartoffel.potatocannon.strategy.withDescription
import io.kotest.matchers.shouldBe


val expect200StatusCode = Expectation("Response Code is 200 OK") { result ->
    result.statusCode shouldBe 200
}

val expectHelloResponseText = Check { result ->
    result.responseText() shouldBe "Hello"
}.withDescription("Response is Hello")


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