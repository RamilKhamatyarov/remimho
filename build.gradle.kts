import org.gradle.internal.impldep.org.eclipse.jgit.util.RawCharUtil.trimTrailingWhitespace
import org.jetbrains.kotlin.gradle.internal.builtins.StandardNames.FqNames.target
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.allopen") version "2.3.0"
    id("io.quarkus") version "3.30.8"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("com.diffplug.spotless") version "8.2.1"
}

group = "ru.rkhamatyarov"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.31.1"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-websockets")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-logging-json")

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
        freeCompilerArgs.addAll("-Xjvm-default=all")
    }
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

ktlint {
    version.set("1.2.1")
}

spotless {
    kotlin {
        ktlint()
        target("**/*.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
