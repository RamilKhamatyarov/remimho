package ru.rkhamatyarov

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import javafx.application.Application
import javafx.application.Platform
import org.jboss.logging.Logger
import ru.rkhamatyarov.engine.Whiteboard

class Main : QuarkusApplication {
    private val log = Logger.getLogger(javaClass.name)

    override fun run(vararg args: String?): Int {
        try {
            Thread.sleep(500)

            Thread {
                try {
                    log.info("Starting JavaFX application...")
                    Application.launch(Whiteboard::class.java, *args)
                } catch (e: Exception) {
                    log.error("Failed to start JavaFX", e)
                    Platform.exit()
                    Quarkus.asyncExit()
                }
            }.start()

            Quarkus.waitForExit()
        } catch (e: Exception) {
            log.error("Quarkus startup failed", e)
            return 1
        }
        return 0
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            System.setProperty("prism.verbose", "false")
            System.setProperty("prism.order", "sw")
            System.setProperty("quantum.multithreaded", "false")

            Quarkus.run(Main::class.java, *args)
        }
    }
}
