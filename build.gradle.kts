plugins {
    id("java")
    id("application")
    id("io.quarkus") version "3.30.2"
    id("org.jetbrains.kotlin.jvm") version "2.2.21"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("com.diffplug.spotless") version "8.1.0"
}

group = "ru.rkhamatyarov"
version = "1.0.0"

repositories {
    mavenCentral()
}

val javafxVersion = "17.0.8"
val platform =
    when {
        System.getProperty("os.name").contains("win", ignoreCase = true) -> "win"
        System.getProperty("os.name").contains("mac", ignoreCase = true) -> "mac"
        else -> "linux"
    }

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.30.2"))

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-core")

    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.10.2")

    implementation("org.slf4j:slf4j-simple:2.0.9")
    implementation("org.jboss.logging:jboss-logging:3.5.3.Final")
    implementation("org.jboss.slf4j:slf4j-jboss-logmanager:2.0.0.Final")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.1")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.2.21")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.2.21")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.mockk:mockk:1.14.7")
}

application {
    mainClass = "ru.rkhamatyarov.Main"
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    named<JavaExec>("run") {
        jvmArgs =
            listOf(
                "--add-opens=java.base/java.lang=ALL-UNNAMED",
                "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
                "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
                "--add-opens=javafx.graphics/com.sun.javafx.application=ALL-UNNAMED",
                "--add-opens=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED",
                "-Dprism.verbose=false",
                "-Dprism.order=sw",
                "-Dquantum.multithreaded=false",
                "-Dfile.encoding=UTF-8",
            )
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.register<Jar>("fatJar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Main-Class" to "ru.rkhamatyarov.Main",
            "Implementation-Title" to "Remimho",
            "Implementation-Version" to version,
            "Implementation-Vendor" to "Ramil Khamatyarov",
        )
    }

    from(sourceSets.main.get().output)
    from({
        configurations.runtimeClasspath
            .get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/versions/**")

    archiveFileName.set("remimho-$version-all.jar")
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

spotless {
    kotlin {
        ktlint()
        target("**/*.kt")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
