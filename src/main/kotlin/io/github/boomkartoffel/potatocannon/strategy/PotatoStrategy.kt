package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.Result
import io.github.boomkartoffel.potatocannon.potato.Expectation

sealed interface PotatoConfiguration

class ResultVerification(private val expectation: Expectation) : PotatoConfiguration, CannonConfiguration {
    fun verify(result: Result) {
        expectation.verify(result)
    }
}
