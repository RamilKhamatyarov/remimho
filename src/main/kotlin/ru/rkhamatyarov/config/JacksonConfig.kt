package ru.rkhamatyarov.config

import io.quarkus.jackson.JsonMapperBuilderCustomizer
import jakarta.enterprise.context.ApplicationScoped
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

@ApplicationScoped
class JacksonConfig : JsonMapperBuilderCustomizer {
    override fun customize(builder: JsonMapper.Builder) {
        builder.addModule(kotlinModule())
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
    }
}
