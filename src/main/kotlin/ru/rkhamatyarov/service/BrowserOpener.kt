package ru.rkhamatyarov.service

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.jboss.logging.Logger
import java.io.IOException
import java.net.Socket

@ApplicationScoped
class BrowserOpener {
    private val log = Logger.getLogger(javaClass)

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        Thread {
            try {
                waitForPort(8080, 5000)
                openBrowser("http://localhost:8080")
            } catch (e: Exception) {
                log.warn("Error on browswer opener", e)
            }
        }.start()
    }

    private fun waitForPort(
        port: Int,
        timeoutMillis: Long,
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try {
                Socket("localhost", port).close()
                return
            } catch (e: IOException) {
                Thread.sleep(200)
            }
        }
        throw RuntimeException("Port $port annaccessable $timeoutMillis ms")
    }

    private fun openBrowser(url: String) {
        val os = System.getProperty("os.name").lowercase()
        val command =
            when {
                os.contains("win") -> {
                    listOf("cmd", "/c", "start", url)
                }

                os.contains("mac") -> {
                    listOf("open", url)
                }

                os.contains("nix") || os.contains("nux") -> {
                    listOf("xdg-open", url)
                }

                else -> {
                    log.warn("Unsupported OS: $os")
                    return
                }
            }
        try {
            ProcessBuilder(command).start()
            log.info("Browser opened on $url")
        } catch (e: Exception) {
            log.error("Error on opening browser", e)
        }
    }
}
