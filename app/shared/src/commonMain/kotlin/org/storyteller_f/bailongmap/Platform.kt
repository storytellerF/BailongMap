package org.storyteller_f.bailongmap

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform