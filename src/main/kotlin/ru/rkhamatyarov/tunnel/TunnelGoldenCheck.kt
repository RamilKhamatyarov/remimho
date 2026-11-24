package ru.rkhamatyarov.tunnel

import java.nio.file.Path

/** Blocking parity check: the committed tunnel goldens must replay identically on every machine. */
object TunnelGoldenCheck {
    fun mismatches(projectDir: Path): List<String> = TunnelGoldenFixtures.mismatches(projectDir)
}
