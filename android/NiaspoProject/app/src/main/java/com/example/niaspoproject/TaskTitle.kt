package com.example.niaspoproject

internal object TaskTitle {
    fun normalize(title: String): String = title.trim()

    fun isValid(title: String): Boolean = normalize(title).length in 1..120
}
