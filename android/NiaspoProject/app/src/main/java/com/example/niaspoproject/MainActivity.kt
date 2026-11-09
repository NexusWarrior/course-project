package com.example.niaspoproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.niaspoproject.ui.theme.NiaspoProjectTheme
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NiaspoProjectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TasksScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(modifier: Modifier = Modifier) {
    var titles by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            titles = loadTaskTitles()
        } catch (e: IOException) {
            error = "Не удалось загрузить задачи: ${e.message}"
        } catch (e: JSONException) {
            error = "Сервер вернул данные в неожиданном формате"
        }
    }

    Column(modifier.padding(16.dp)) {
        val loadedTitles = titles
        val errorMessage = error
        when {
            errorMessage != null -> Text(errorMessage)
            loadedTitles == null -> Text("Загрузка задач…")
            loadedTitles.isEmpty() -> Text("Задач пока нет")
            else -> loadedTitles.forEach { title ->
                Text(title, modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

private suspend fun loadTaskTitles(): List<String> = withContext(Dispatchers.IO) {
    val connection = URL("http://10.0.2.2:8080/tasks")
        .openConnection() as HttpURLConnection

    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val tasks = JSONArray(body)
        List(tasks.length()) { index -> tasks.getJSONObject(index).getString("title") }
    } finally {
        connection.disconnect()
    }
}