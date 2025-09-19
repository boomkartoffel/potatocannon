package io.github.boomkartoffel.potatocannon

import io.github.boomkartoffel.potatocannon.potato.Potato

operator fun Potato.times(other: Int): List<Potato> = List(other) { this }