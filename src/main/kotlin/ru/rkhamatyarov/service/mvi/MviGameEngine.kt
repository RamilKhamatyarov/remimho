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
    private val reportReducerFailure: (GameAction, Exception) -> Unit,
) : AutoCloseable {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(context + job)
    private val actions = Channel<GameAction>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow(MviGameState())

    val state: StateFlow<MviGameState> = mutableState.asStateFlow()

    init {
        scope.launch {
            for (action in actions) {
                try {
                    mutableState.value = reduce(mutableState.value, action)
                } catch (error: Exception) {
                    reportReducerFailure(action, error)
                }
            }
        }
    }

    @Suppress("unused")
    constructor() : this(Dispatchers.Default, ::logReducerFailure)

    @Suppress("UNUSED_PARAMETER")
    internal constructor(
        context: CoroutineContext,
        testOnly: Boolean,
    ) : this(context, { _, _ -> })

    fun tryDispatch(action: GameAction): Boolean = actions.trySend(action).isSuccess

    @PreDestroy
    override fun close() {
        actions.close()
        job.cancel()
    }

    companion object {
        private val log = Logger.getLogger(MviGameEngine::class.java)

        private fun logReducerFailure(
            action: GameAction,
            error: Exception,
        ) {
            log.warn("MVI reducer failed for action=$action: ${error.message}")
        }
    }
}
