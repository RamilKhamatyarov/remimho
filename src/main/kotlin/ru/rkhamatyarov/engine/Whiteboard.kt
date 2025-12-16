package ru.rkhamatyarov.engine

import io.quarkus.runtime.Quarkus
import jakarta.enterprise.inject.spi.CDI
import javafx.application.Application
import javafx.application.Platform
import javafx.stage.Stage
import javafx.stage.WindowEvent
import ru.rkhamatyarov.service.WhiteboardService

class Whiteboard : Application() {
    companion object {
        lateinit var whiteboardService: WhiteboardService
    }

    override fun init() {
        try {
            // Get CDI container and inject the service
            val cdiContainer = CDI.current()
            whiteboardService = cdiContainer.select(WhiteboardService::class.java).get()
        } catch (e: Exception) {
            throw RuntimeException("Failed to initialize WhiteboardService via CDI", e)
        }
    }

    override fun start(stage: Stage) {
        try {
            stage.setOnCloseRequest { _: WindowEvent? ->
                Platform.exit()
                Thread {
                    Quarkus.asyncExit()
                }.start()
            }

            whiteboardService.startGame(stage)
        } catch (e: Exception) {
            throw RuntimeException("Failed to start game", e)
        }
    }
}
