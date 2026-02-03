package ru.rkhamatyarov.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.kotlinModule
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Produces

/**
 * Jackson ObjectMapper configuration
 * Ensures ObjectMapper is properly configured and available for injection
 */

@ApplicationScoped
class JacksonConfig {
    @Produces
    fun objectMapper(): ObjectMapper =
        ObjectMapper().apply {
            registerModule(kotlinModule())

            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

            enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
        }
}
