import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("application")
    id("io.quarkus") version "3.30.6"
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("com.diffplug.spotless") version "8.1.0"
    id("jacoco")
}

group = "ru.rkhamatyarov"
version = "1.0.0"

repositories {
    mavenCentral()
}

val javafxVersion = "17.0.8"
val osName = System.getProperty("os.name").lowercase()
val platform =
    when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> "mac"
        else -> "linux"
    }

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:3.30.5"))

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-core")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.10.2")

    implementation("org.openjfx:javafx-base:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$platform")
    implementation("org.openjfx:javafx-fxml:$javafxVersion:$platform")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.1")
    testImplementation("org.testfx:testfx-junit5:4.0.18")
    testImplementation("org.jetbrains.kotlin:kotlin-reflect:2.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.3.0")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.mockk:mockk:1.14.7")
}

application {
    mainClass.set("ru.rkhamatyarov.Main")
}

tasks.test {
    jvmArgs = listOf("-XX:+EnableDynamicAgentLoading")
    useJUnitPlatform()
}

tasks.register("cleanJpackage") {
    group = "build"
    doLast {
        file(layout.buildDirectory.dir("jpackage-libs")).deleteRecursively()
        file(layout.buildDirectory.dir("installers")).deleteRecursively()
    }
}

tasks.register<Jar>("createFatJar") {
    group = "build"
    dependsOn("classes")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            mapOf(
                "Main-Class" to "ru.rkhamatyarov.Main",
                "Implementation-Title" to "Remimho",
                "Implementation-Version" to project.version,
                "Implementation-Vendor" to "Ramil Khamatyarov",
            ),
        )
    }

    from(sourceSets["main"].output)

    doFirst {
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }

    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/versions/**")
    exclude("META-INF/INDEX.LIST")

    archiveFileName.set("remimho-$version-all.jar")
    destinationDirectory.set(layout.buildDirectory.dir("jpackage-libs"))
}

tasks.register<Exec>("packageWithJpackage") {
    group = "build"
    dependsOn("createFatJar")

    doFirst {
        val javaHome = System.getProperty("java.home")
        var jpackage = "$javaHome${File.separator}bin${File.separator}jpackage.exe"

        if (!file(jpackage).exists()) {
            jpackage = "$javaHome${File.separator}bin${File.separator}jpackage"
        }

        commandLine(
            jpackage,
            "--input",
            layout.buildDirectory
                .dir("jpackage-libs")
                .get()
                .asFile.absolutePath,
            "--dest",
            layout.buildDirectory
                .dir("installers")
                .get()
                .asFile.absolutePath,
            "--name",
            "Remimho",
            "--main-jar",
            "remimho-$version-all.jar",
            "--main-class",
            "ru.rkhamatyarov.Main",
            "--app-version",
            version,
            "--vendor",
            "Ramil Khamatyarov",
            "--description",
            "Conway's Game of Life Explorer",
            "--icon",
            file("src/main/resources/remimho.ico").absolutePath,
            "--type",
            "exe",
            "--java-options",
            "-Xmx512m",
        )
    }
}

tasks.register("buildFullInstaller") {
    group = "build"
    dependsOn("cleanJpackage", "assemble", "packageWithJpackage")
}

tasks.register("checkJpackageReadiness") {
    group = "verification"

    doLast {
        val javaHome = System.getProperty("java.home")
        val jpackage = file("$javaHome${File.separator}bin${File.separator}jpackage")
        val icon = file("src/main/resources/remimho.ico")
        val license = file("LICENSE.txt")

        println("\nChecking jpackage prerequisites:")
        println(if (jpackage.exists()) "OK: jpackage found" else "ERROR: jpackage not found (requires JDK 16+)")
        println(if (icon.exists()) "OK: Icon found" else "WARNING: Icon not found")
        println(if (license.exists()) "OK: License found" else "WARNING: License not found")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
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

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
