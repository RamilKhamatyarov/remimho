package ru.rkhamatyarov.tunnel

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TunnelGoldenFreshProcessTest {
    private val projectDir: Path = Path.of(System.getProperty("user.dir"))

    @Test
    fun `each golden replays identically in two fresh processes`() {
        assertTrue(
            Files.isDirectory(projectDir.resolve(TunnelGoldenFixtures.GOLDEN_DIRECTORY)),
            "missing ${TunnelGoldenFixtures.GOLDEN_DIRECTORY}; run updateTunnelGoldens first",
        )
        TunnelGoldenScenarios.all.forEach { scenario ->
            val first = TunnelReplayCli.inFreshProcess(scenario.name)
            val second = TunnelReplayCli.inFreshProcess(scenario.name)

            assertEquals(first, second, "${scenario.name} differed between fresh processes")
            assertEquals(
                TunnelReplayCli.committedTrace(projectDir, scenario.name).render(),
                first,
                "${scenario.name} differs from the committed golden",
            )
        }
    }
}
