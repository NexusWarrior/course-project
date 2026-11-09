package com.nexus

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ServerTest {
    @Test
    fun `tasks endpoint returns demo tasks`() = testApplication {
        configure()

        val response = client.get("/tasks")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertContains(body, "Настроить сервер")
        assertContains(body, "Подключить Android-приложение")
    }
}