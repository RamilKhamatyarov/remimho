package ru.rkhamatyarov.engine

import javafx.application.Application
import javafx.stage.Stage
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.stereotype.Component
import ru.rkhamatyarov.WhiteboardApplication
import ru.rkhamatyarov.service.WhiteboardService

@Component
class Whiteboard() : Application() {

    private lateinit var whiteboardService: WhiteboardService

    override fun init() {
        val context = AnnotationConfigApplicationContext(WhiteboardApplication::class.java)
        whiteboardService = context.getBean(WhiteboardService::class.java)
    }

    override fun start(stage: Stage) {
        whiteboardService.startGame(stage)
    }

    fun getWhiteboardService(): WhiteboardService {
        return whiteboardService
    }
}
