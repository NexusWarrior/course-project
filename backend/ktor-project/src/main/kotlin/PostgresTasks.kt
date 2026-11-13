package com.nexus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

internal data class Task(val id: Int, val title: String, val completed: Boolean)
internal data class TaskInput(val title: String, val completed: Boolean)

internal interface TaskStore {
    suspend fun list(): List<Task>
    suspend fun create(task: TaskInput): Task
    suspend fun update(id: Int, task: TaskInput): Task?
    suspend fun delete(id: Int): Boolean
    suspend fun checkReady()
}

internal class PostgresTasks : TaskStore {
    override suspend fun list(): List<Task> = withConnection { connection ->
        connection.prepareStatement("SELECT id, title, completed FROM tasks ORDER BY id").use { statement ->
            statement.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) add(rows.task())
                }
            }
        }
    }

    override suspend fun create(task: TaskInput): Task = withConnection { connection ->
        connection.prepareStatement(
            "INSERT INTO tasks (title, completed) VALUES (?, ?) RETURNING id, title, completed"
        ).use { statement ->
            statement.setString(1, task.title)
            statement.setBoolean(2, task.completed)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.task()
            }
        }
    }

    override suspend fun update(id: Int, task: TaskInput): Task? = withConnection { connection ->
        connection.prepareStatement(
            "UPDATE tasks SET title = ?, completed = ? WHERE id = ? RETURNING id, title, completed"
        ).use { statement ->
            statement.setString(1, task.title)
            statement.setBoolean(2, task.completed)
            statement.setInt(3, id)
            statement.executeQuery().use { rows -> if (rows.next()) rows.task() else null }
        }
    }

    override suspend fun delete(id: Int): Boolean = withConnection { connection ->
        connection.prepareStatement("DELETE FROM tasks WHERE id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeUpdate() == 1
        }
    }

    override suspend fun checkReady(): Unit = withConnection { connection ->
        connection.prepareStatement("SELECT id, title, completed FROM tasks LIMIT 0").use { statement ->
            statement.queryTimeout = 3
            statement.executeQuery().close()
        }
    }

    private suspend fun <T> withConnection(block: (Connection) -> T): T = withContext(Dispatchers.IO) {
        val url = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:5432/tasks"
        val user = System.getenv("DB_USER") ?: "tasks_user"
        val password = System.getenv("DB_PASSWORD")
            ?: requireNotNull(System.getenv("DB_PASSWORD_FILE")) {
                "Задайте DB_PASSWORD или DB_PASSWORD_FILE"
            }.let { java.io.File(it).readText().trimEnd('\n', '\r') }

        val properties = java.util.Properties().apply {
            setProperty("user", user)
            setProperty("password", password)
            setProperty("connectTimeout", "3")
            setProperty("socketTimeout", "5")
        }
        DriverManager.getConnection(url, properties).use(block)
    }

    private fun ResultSet.task() = Task(getInt("id"), getString("title"), getBoolean("completed"))
}
