package ru.rkhamatyarov.service.mvi

import jakarta.annotation.PreDestroy
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jboss.logging.Logger
import kotlin.coroutines.CoroutineContext

@ApplicationScoped
class MviGameEngine private constructor(
    context: CoroutineContext,
) : AutoCloseable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(context + job)
    private val actions = Channel<GameAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(MviGameState())
    private val log = Logger.getLogger(MviGameEngine::class.java)

    val state: StateFlow<MviGameState> = mutableState.asStateFlow()

    init {
        scope.launch {
            for (action in actions) {
                try {
                    mutableState.value = reduce(mutableState.value, action)
                } catch (error: Exception) {
                    log.error("MVI reducer error: ${error.message}")
                }
            }
        }
    }

    @Suppress("unused")
    constructor() : this(Dispatchers.Default)

    @Suppress("UNUSED_PARAMETER")
    internal constructor(
        context: CoroutineContext,
        testOnly: Boolean,
    ) : this(context)

    fun tryDispatch(action: GameAction): Boolean = actions.trySend(action).isSuccess

    @PreDestroy
    override fun close() {
        actions.close()
        job.cancel()
    }
}
