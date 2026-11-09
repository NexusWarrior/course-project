package com.nexus

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import io.ktor.server.config.MapApplicationConfig

class ServerTest {
    @Test
    fun `tasks endpoint returns demo tasks`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }
        application {
            configureTaskRoutes {
                """
        [
          {"id": 1, "title": "Настроить сервер", "completed": true},
          {"id": 2, "title": "Подключить Android-приложение", "completed": false}
        ]
        """.trimIndent()
            }
        }

        val response = client.get("/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Настроить сервер")
        assertContains(body, "Подключить Android-приложение")
    }
}