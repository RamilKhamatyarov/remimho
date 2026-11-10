package ru.rkhamatyarov.replay

import org.junit.jupiter.api.Test
import ru.rkhamatyarov.proto.ReplayFile
import ru.rkhamatyarov.service.mvi.GameAction
import ru.rkhamatyarov.service.mvi.MviGameState
import ru.rkhamatyarov.service.mvi.MviPuck
import ru.rkhamatyarov.service.mvi.reduce
import ru.rkhamatyarov.util.ReplayMother.replay
import java.security.MessageDigest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SpinReplayTest {
    @Test
    fun `historical spin snapshots remain unchanged`() {
        val starts =
            listOf(
                MviGameState(puck = MviPuck(x = 15.0, y = 224.0, vx = 10.0, vy = 2000.0), paddle1Velocity = 20.0),
                MviGameState(puck = MviPuck(x = 40.0, vx = -1000.0, vy = 0.0), paddle1Velocity = -20.0),
                MviGameState(
                    puck = MviPuck(y = 20.0, vx = 100.0, vy = -200.0, spin = 0.5, spinRemainingNs = 750_000_000L),
                ),
            )
        val expected =
            listOf(
                "7826d08162106bb0fb8669bf18094f9ad9e8db595e0cba46c853fa4a97d09863",
                "51dfa468278a5370445e5daff8d2518828236fa54706ebc7cf1f0ac19ee770f6",
                "cd91c258bab8fd1f212deff28e45b26ede5ef1b3231f2e476ae4c3fb040c2606",
            )
        for (version in listOf("", "1", "2")) {
            for ((index, start) in starts.withIndex()) {
                val states = HeadlessReplayImporter().importSampledStates(replay(version, start), 1)
                val digest = MessageDigest.getInstance("SHA-256")
                states.forEach { digest.update(ReplayConverter.stateToSnapshot(it).toByteArray()) }
                assertEquals(
                    expected[index],
                    digest.digest().joinToString("") { "%02x".format(it) },
                    "$version/$index",
                )
            }
        }
    }

    @Test
    fun `version three reproduces live snapshot bytes after edge swipe`() {
        val start =
            MviGameState(
                puck = MviPuck(x = 15.0, y = 224.0, vx = 10.0, vy = 2000.0),
                paddle1Velocity = 20.0,
            )
        val live = (1..60).fold(start) { state, tick -> reduce(state, GameAction.Tick(0.016, tick * 16_000_000L)) }
        val file = ReplayFile.parseFrom(replay("3", start).toByteArray())
        val imported = HeadlessReplayImporter().import(file).finalState
        assertEquals(live, imported)
        assertContentEquals(
            ReplayConverter.stateToSnapshot(live).toByteArray(),
            ReplayConverter.stateToSnapshot(imported).toByteArray(),
        )
    }
}
