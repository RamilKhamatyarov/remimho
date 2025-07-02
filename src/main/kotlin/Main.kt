package ru.rkhamatyarov

import io.quarkus.runtime.Quarkus
import io.quarkus.runtime.QuarkusApplication
import jakarta.inject.Singleton
import javafx.application.Application
import org.jboss.logging.Logger
import ru.rkhamatyarov.engine.Whiteboard

@Singleton
class Main : QuarkusApplication {
    private val log = Logger.getLogger(Main::class.java)

    override fun run(vararg args: String?): Int {
        Thread {
            try {
                Application.launch(Whiteboard::class.java)
            } catch (e: Exception) {
                log.error("Failed to start JavaFX", e)
                Quarkus.asyncExit()
            }
        }.start()

        Quarkus.waitForExit()
        return 0
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            Quarkus.run(Main::class.java, *args)
        }
    }
}
