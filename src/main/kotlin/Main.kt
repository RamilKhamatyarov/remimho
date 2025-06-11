package ru.rkhamatyarov

import io.quarkus.arc.Arc
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import jakarta.inject.Singleton
import javafx.application.Application
import ru.rkhamatyarov.engine.Whiteboard
import ru.rkhamatyarov.service.WhiteboardService

@Singleton
class Main : QuarkusApplication {
    override fun run(vararg args: String?): Int {
        val handle = Arc.container().instance(WhiteboardService::class.java)
        if (handle.isAvailable) {
            Whiteboard.whiteboardService = handle.get()
            Application.launch(Whiteboard::class.java, *args.filterNotNull().toTypedArray())
            return 0
        } else {
            println("WhiteboardService bean not found!")
            return 1
        }
    }
}

fun main(args: Array<String>) {
    Quarkus.run(Main::class.java, *args)
}
