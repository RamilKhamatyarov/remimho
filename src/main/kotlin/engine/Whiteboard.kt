package ru.rkhamatyarov.engine

import jakarta.enterprise.inject.spi.CDI
import javafx.application.Application
import javafx.stage.Stage
import ru.rkhamatyarov.service.WhiteboardService

class Whiteboard : Application() {
    companion object {
        lateinit var whiteboardService: WhiteboardService
    }

    override fun init() {
        val cdiContainer = CDI.current()
        whiteboardService = cdiContainer.select(WhiteboardService::class.java).get()
    }

    override fun start(stage: Stage) {
        whiteboardService.startGame(stage)
    }
}
