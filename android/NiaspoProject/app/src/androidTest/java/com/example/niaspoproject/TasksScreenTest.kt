package com.example.niaspoproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TasksScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var serverTasks: List<Task>

    @Before
    fun verifyApiConnection() {
        serverTasks = try {
            runBlocking { TaskApi.list() }
        } catch (error: IOException) {
            throw AssertionError(
                "Эмулятор не смог получить GET /tasks с ${BuildConfig.TASK_API_URL}. " +
                    "Проверьте запуск Ktor и PostgreSQL через Docker Compose.",
                error
            )
        }
    }

    @Test
    fun displaysTaskLoadedFromServer() {
        val task = requireNotNull(serverTasks.firstOrNull()) {
            "GET /tasks вернул пустой список. Добавьте хотя бы одну задачу для проверки загрузки."
        }
        awaitTitle(task.title)
    }

    @Test
    fun createsEditsCompletesAndDeletesTask() {
        awaitRequest()
        val title = "Проверка Android ${System.currentTimeMillis()}"
        val edited = "$title (изменено)"

        composeRule.onNodeWithText("Добавить").performClick()
        composeRule.onNodeWithTag("taskTitle").performTextInput(title)
        composeRule.onNodeWithText("Сохранить").performClick()
        awaitTitle(title)
        composeRule.onNodeWithTag("edit:$title").performClick()
        composeRule.onNodeWithTag("taskTitle").performTextReplacement(edited)
        composeRule.onNodeWithText("Сохранить").performClick()
        awaitTitle(edited)
        composeRule.onNodeWithContentDescription("Выполнено: $edited").performClick()

        // Wait for the update to finish before checking persisted state after activity recreation.
        awaitRequest()
        composeRule.activityRule.scenario.recreate()
        awaitTitle(edited)
        composeRule.onNodeWithContentDescription("Выполнено: $edited").assertIsOn()
        composeRule.onNodeWithTag("delete:$edited").performClick()
        composeRule.onNodeWithText("Подтвердить").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(edited).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitRequest() {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Выполняется запрос…").fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitTitle(title: String) {
        awaitRequest()
        composeRule.onNodeWithTag("tasksList").performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
