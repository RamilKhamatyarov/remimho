package ru.rkhamatyarov.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
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
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
        }
}
