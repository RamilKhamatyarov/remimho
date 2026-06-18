package ru.rkhamatyarov.service

interface ActorMailbox<T> {
    val depth: Int

    fun trySend(value: T): Boolean

    suspend fun receive(): T?

    fun close()
}
