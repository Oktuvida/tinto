package com.tinto

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TintoBackendApplication

fun main(args: Array<String>) {
    runApplication<TintoBackendApplication>(*args)
}
