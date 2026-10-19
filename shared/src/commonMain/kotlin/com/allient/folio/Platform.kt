package com.allient.folio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform