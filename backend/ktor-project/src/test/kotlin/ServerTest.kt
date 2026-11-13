package com.nexus

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class ServerTest {
    @Test
    fun taskLifecycle() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureTaskRoutes(MemoryTasks()) }

        assertEquals("[]", client.get("/tasks").bodyAsText())

        val created = client.post("/tasks") { setBody("""{"title":"  Проверить стенд  "}""") }
        assertEquals(HttpStatusCode.Created, created.status)
        assertEquals("/tasks/1", created.headers["Location"])
        assertContains(created.bodyAsText(), """"title":"Проверить стенд"""")

        val updated = client.put("/tasks/1") { setBody("""{"title":"Стенд проверен","completed":true}""") }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertContains(client.get("/tasks").bodyAsText(), """"completed":true""")

        assertEquals(HttpStatusCode.NoContent, client.delete("/tasks/1").status)
        assertEquals("[]", client.get("/tasks").bodyAsText())
        assertEquals(HttpStatusCode.NotFound, client.delete("/tasks/1").status)
    }

    @Test
    fun invalidInputAndMissingTask() = testApplication {
        environment { config = MapApplicationConfig() }
        application { configureTaskRoutes(MemoryTasks()) }

        assertEquals(HttpStatusCode.BadRequest, client.post("/tasks") { setBody("{") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/tasks") { setBody("""{"title":" "}""") }.status)
        assertEquals(HttpStatusCode.BadRequest, client.delete("/tasks/-1").status)
        assertEquals(
            HttpStatusCode.NotFound,
            client.put("/tasks/99") { setBody("""{"title":"Нет такой задачи"}""") }.status
        )
    }

    @Test
    fun unavailableDatabaseFailsReadiness() = testApplication {
        environment { config = MapApplicationConfig() }
        application {
            configureTaskRoutes(object : TaskStore by MemoryTasks() {
                override suspend fun checkReady() { throw SQLException("Database unavailable") }
            })
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/health").status)
    }
}

private class MemoryTasks : TaskStore {
    private val tasks = linkedMapOf<Int, Task>()
    private var nextId = 1

    override suspend fun list() = tasks.values.toList()

    override suspend fun create(task: TaskInput): Task =
        Task(nextId++, task.title, task.completed).also { tasks[it.id] = it }

    override suspend fun update(id: Int, task: TaskInput): Task? =
        tasks.replace(id, Task(id, task.title, task.completed))?.let { tasks.getValue(id) }

    override suspend fun delete(id: Int) = tasks.remove(id) != null
    override suspend fun checkReady() = Unit
}
