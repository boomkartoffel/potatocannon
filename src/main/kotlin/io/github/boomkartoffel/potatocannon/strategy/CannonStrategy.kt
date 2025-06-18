package io.github.boomkartoffel.potatocannon.strategy

import io.github.boomkartoffel.potatocannon.cannon.Mode

sealed interface CannonConfiguration

class FireMode(val mode: Mode) : CannonConfiguration

