package com.example.knotes

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform