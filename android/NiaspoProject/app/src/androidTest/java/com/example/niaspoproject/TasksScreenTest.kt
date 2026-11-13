package com.example.niaspoproject

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class TasksScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun displaysTaskLoadedFromServer() {
        awaitTitle("Подключить Android-приложение")
    }

    @Test
    fun createsEditsCompletesAndDeletesTask() {
        awaitTitle("Подключить Android-приложение")
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
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Выполняется запрос…").fetchSemanticsNodes().isEmpty()
        }
        composeRule.activityRule.scenario.recreate()
        awaitTitle(edited)
        composeRule.onNodeWithContentDescription("Выполнено: $edited").assertIsOn()
        composeRule.onNodeWithTag("delete:$edited").performClick()
        composeRule.onNodeWithText("Подтвердить").performClick()
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(edited).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun awaitTitle(title: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText("Выполняется запрос…").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("tasksList").performScrollToNode(hasText(title))
        composeRule.onNodeWithText(title).assertIsDisplayed()
    }
}
