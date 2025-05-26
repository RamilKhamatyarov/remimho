package ru.rkhamatyarov

import javafx.application.Application
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import ru.rkhamatyarov.engine.Whiteboard

@SpringBootApplication
class WhiteboardApplication

fun main() {
    runApplication<WhiteboardApplication>()
    Application.launch(Whiteboard::class.java)
}
