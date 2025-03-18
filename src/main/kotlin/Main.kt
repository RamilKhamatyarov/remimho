package ru.rkhamatyarov

import javafx.application.Application
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import ru.rkhamatyarov.engine.Whiteboard

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.

@SpringBootApplication
class WhiteboardApplication

fun main() {
    Thread {
        runApplication<WhiteboardApplication>()
    }.start()

    Application.launch(Whiteboard::class.java)
}