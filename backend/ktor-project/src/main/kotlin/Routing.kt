package com.nexus

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.DriverManager

fun Application.configureRouting() {
    configureTaskRoutes { loadTasks() }
}

internal fun Application.configureTaskRoutes(loadTasks: suspend () -> String) {
    routing {
        get("/tasks") {
            call.respondText(loadTasks(), ContentType.Application.Json)
        }
    }
}

private suspend fun loadTasks(): String = withContext(Dispatchers.IO) {
    val url = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/tasks"
    val user = System.getenv("DB_USER") ?: "tasks_user"
    val password = requireNotNull(System.getenv("DB_PASSWORD")) {
        "Задай переменную окружения DB_PASSWORD"
    }

    val sql = """
        SELECT COALESCE(json_agg(row_to_json(task) ORDER BY task.id), '[]'::json)
        FROM (SELECT id, title, completed FROM tasks) AS task
    """.trimIndent()

    DriverManager.getConnection(url, user, password).use { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { result ->
                check(result.next())
                result.getString(1)
            }
        }
    }
}