package com.nexus

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import java.sql.SQLException

private val json = ObjectMapper()

fun Application.configureRouting() = configureTaskRoutes(PostgresTasks())

internal fun Application.configureTaskRoutes(tasks: TaskStore) {
    routing {
        get("/health") {
            try {
                tasks.checkReady()
                call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
            } catch (error: SQLException) {
                this@configureTaskRoutes.environment.log.warn("Task database unavailable", error)
                call.respondText(
                    """{"status":"unavailable"}""",
                    ContentType.Application.Json,
                    HttpStatusCode.ServiceUnavailable
                )
            }
        }
        route("/tasks") {
            get {
                call.respondJson(tasks.list())
            }
            post {
                val task = call.readTask() ?: return@post
                val created = tasks.create(task)
                call.response.headers.append("Location", "/tasks/${created.id}")
                call.respondJson(created, HttpStatusCode.Created)
            }
            put("/{id}") {
                val id = call.taskId() ?: return@put
                val task = call.readTask() ?: return@put
                val updated = tasks.update(id, task)
                if (updated == null) call.respond(HttpStatusCode.NotFound)
                else call.respondJson(updated)
            }
            delete("/{id}") {
                val id = call.taskId() ?: return@delete
                call.respond(if (tasks.delete(id)) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
            }
        }
    }
}

private suspend fun ApplicationCall.respondJson(value: Any, status: HttpStatusCode = HttpStatusCode.OK) {
    respondText(json.writeValueAsString(value), ContentType.Application.Json, status)
}

private suspend fun ApplicationCall.taskId(): Int? {
    val id = parameters["id"]?.toIntOrNull()?.takeIf { it > 0 }
    if (id == null) respondJson(mapOf("error" to "Некорректный идентификатор"), HttpStatusCode.BadRequest)
    return id
}

private suspend fun ApplicationCall.readTask(): TaskInput? {
    val node = try {
        json.readTree(receiveText()) as? ObjectNode
    } catch (_: JsonProcessingException) {
        null
    }
    val title = node?.get("title")?.takeIf { it.isTextual }?.asText()?.trim()
    val completed = node?.get("completed")
    if (title.isNullOrEmpty() || title.length > 120 || (completed != null && !completed.isBoolean)) {
        respondJson(
            mapOf("error" to "Укажите название длиной от 1 до 120 символов и логическое значение completed"),
            HttpStatusCode.BadRequest
        )
        return null
    }
    return TaskInput(title, completed?.booleanValue() ?: false)
}
