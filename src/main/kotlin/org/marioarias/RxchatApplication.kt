package org.marioarias

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
open class RxchatApplication

fun main(args: Array<String>) {
    SpringApplication.run(RxchatApplication::class.java, *args)
}
