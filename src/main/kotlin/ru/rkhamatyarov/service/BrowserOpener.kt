package ru.rkhamatyarov.service

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.io.IOException
import java.net.Socket
import java.net.URI

@ApplicationScoped
class BrowserOpener {
    private val log = Logger.getLogger(javaClass)

    @ConfigProperty(name = "remimho.browser.open", defaultValue = "true")
    var openBrowserOnStart: Boolean = true

    @ConfigProperty(name = "remimho.browser.url", defaultValue = "http://localhost:8080")
    lateinit var browserUrl: String

    fun onStart(
        @Observes event: StartupEvent,
    ) {
        if (!openBrowserOnStart) {
            log.debug("Browser auto-open is disabled")
            return
        }

        Thread {
            try {
                val url = URI(browserUrl)
                waitForPort(url.host ?: "localhost", resolvePort(url), 5000)
                openBrowser(browserUrl)
            } catch (e: Exception) {
                log.warn("Error on browser opener", e)
            }
        }.start()
    }

    private fun resolvePort(url: URI): Int =
        when {
            url.port > 0 -> url.port
            url.scheme.equals("https", ignoreCase = true) -> 443
            else -> 80
        }

    private fun waitForPort(
        host: String,
        port: Int,
        timeoutMillis: Long,
    ) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMillis) {
            try {
                Socket(host, port).close()
                return
            } catch (_: IOException) {
                Thread.sleep(200)
            }
        }
        throw RuntimeException("Port $port inaccessible after $timeoutMillis ms")
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
