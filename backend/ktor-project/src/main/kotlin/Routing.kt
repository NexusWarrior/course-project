package com.nexus

import io.ktor.http.ContentType

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/tasks") {
            call.respondText(
                text = """
            [
                {"id": 1, "title": "Настроить сервер", "completed": true},
                {"id": 2, "title": "Подключить Android-приложение", "completed": false}
            ]
        """.trimIndent(),
                contentType = ContentType.Application.Json
            )
        }
    }
}