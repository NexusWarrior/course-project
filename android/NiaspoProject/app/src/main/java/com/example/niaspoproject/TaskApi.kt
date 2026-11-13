package com.example.niaspoproject

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class Task(val id: Int, val title: String, val completed: Boolean)

internal object TaskApi {
    suspend fun list(): List<Task> {
        val data = JSONArray(request("GET", "/tasks"))
        return List(data.length()) { index -> data.getJSONObject(index).toTask() }
    }

    suspend fun create(title: String): Task =
        JSONObject(request("POST", "/tasks", payload(title, false))).toTask()

    suspend fun update(task: Task): Task =
        JSONObject(request("PUT", "/tasks/${task.id}", payload(task.title, task.completed))).toTask()

    suspend fun delete(id: Int) {
        request("DELETE", "/tasks/$id")
    }

    private fun payload(title: String, completed: Boolean) =
        JSONObject().put("title", title).put("completed", completed).toString()

    private fun JSONObject.toTask() = Task(getInt("id"), getString("title"), getBoolean("completed"))

    private suspend fun request(method: String, path: String, body: String? = null): String =
        withContext(Dispatchers.IO) {
            val connection = URI(BuildConfig.TASK_API_URL + path)
                .toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 5_000
                connection.readTimeout = 5_000
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                }
                val status = connection.responseCode
                if (status !in 200..299) throw IOException("HTTP $status")
                if (status == 204) "" else connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
}
