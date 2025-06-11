package ru.rkhamatyarov

import io.quarkus.arc.Arc
import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import jakarta.inject.Singleton
import javafx.application.Application
import org.jboss.logging.Logger
import ru.rkhamatyarov.engine.Whiteboard
import ru.rkhamatyarov.service.WhiteboardService

@Singleton
class Main : QuarkusApplication {
    private val log = Logger.getLogger(Main::class.java)

    override fun run(vararg args: String?): Int {
        val handle = Arc.container().instance(WhiteboardService::class.java)
        if (handle.isAvailable) {
            Whiteboard.whiteboardService = handle.get()
            Application.launch(Whiteboard::class.java, *args.filterNotNull().toTypedArray())
            return 0
        } else {
            log.error("WhiteboardService bean not found!")
            return 1
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Quarkus.run(Main::class.java, *args)
        }
    }
}
