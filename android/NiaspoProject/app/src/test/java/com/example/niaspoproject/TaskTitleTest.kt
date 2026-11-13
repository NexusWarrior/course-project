package com.example.niaspoproject

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskTitleTest {
    @Test
    fun trimsWhitespace() {
        assertEquals("Подготовить стенд", TaskTitle.normalize("  Подготовить стенд  "))
    }

    @Test
    fun validatesTrimmedLength() {
        assertFalse(TaskTitle.isValid("  "))
        assertTrue(TaskTitle.isValid(" " + "я".repeat(120) + " "))
        assertFalse(TaskTitle.isValid("я".repeat(121)))
    }
}
