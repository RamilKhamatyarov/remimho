package ru.rkhamatyarov.service

import ru.rkhamatyarov.model.Line

interface Formula {
    fun createLine(): Line

    val name: String
}
