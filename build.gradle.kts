import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.SourceSetContainer
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.allopen") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("io.quarkus") version "4.0.0"
    id("com.google.protobuf") version "0.10.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.diffplug.spotless") version "8.11.0"
}

group = "ru.rkhamatyarov"
version = "1.0.0"

val protobufVersion = "4.36.2"
val ktlintVersion = "1.2.1"

abstract class GenerateDemoGifTask : JavaExec()

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus:quarkus-bom:3.40.1.2"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-logging-json")

    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.9.3")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.12.0")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-property-jvm:6.2.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        javaParameters = true
        freeCompilerArgs.addAll("-jvm-default=enable")
    }
}

allOpen {
    annotation("jakarta.ws.rs.Path")
    annotation("jakarta.enterprise.context.ApplicationScoped")
    annotation("jakarta.persistence.Entity")
    annotation("io.quarkus.test.junit.QuarkusTest")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                java { }
                kotlin { }
            }
        }
    }
}

configurations.configureEach {
    resolutionStrategy.force(
        "com.google.protobuf:protobuf-java:$protobufVersion",
        "com.google.protobuf:protobuf-kotlin:$protobufVersion",
    )
}

ktlint {
    version.set(ktlintVersion)
    filter {
        exclude { element ->
            element.file.path.contains("build/generated")
        }
    }
}

spotless {
    kotlin {
        ktlint(ktlintVersion)
        target("**/*.kt")
        targetExclude("build/generated/**/*.kt")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

tasks.test {
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}
val mainSourceSet = the<SourceSetContainer>().named("main")

tasks.register<GenerateDemoGifTask>("generateDemoGif") {
    group = "documentation"
    description = "Generates docs/demo.gif from src/test/resources/demo.replay"
    dependsOn("compileKotlin", "compileJava")
    mainClass.set("ru.rkhamatyarov.rendering.DemoGifGenerator")
    classpath = mainSourceSet.get().runtimeClasspath
    args(layout.projectDirectory.asFile.absolutePath)
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    inputs.files(layout.projectDirectory.file("src/test/resources/demo.replay")).optional()
    outputs.file(layout.projectDirectory.file("docs/demo.gif"))
    mustRunAfter("test")
}

if (System.getenv("CI") != "true") {
    tasks.named("build") { dependsOn("generateDemoGif") }
}

// Determinism tools (docs/determinism/README.md). None of these are attached to `build`.
val determinismOutDir = layout.buildDirectory.dir("determinism")

fun registerDeterminismTask(
    name: String,
    summary: String,
    command: String,
    defaultArgs: () -> List<String> = { emptyList() },
) = tasks.register<JavaExec>(name) {
    group = "determinism"
    description = summary
    dependsOn("compileKotlin", "compileJava")
    mainClass.set("ru.rkhamatyarov.determinism.DeterminismCli")
    classpath = mainSourceSet.get().runtimeClasspath
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    // The command travels as a property because `--args` replaces the argument list.
    systemProperty("determinism.command", command)
    argumentProviders.add(CommandLineArgumentProvider { defaultArgs() })
}

val projectPath = layout.projectDirectory.asFile.absolutePath

registerDeterminismTask("traceRecord", "Hashes every step: --args=\"<input> <out.trace>\"", "record")
registerDeterminismTask("traceDump", "Writes all state fields: --args=\"<input> <step> <out.dump>\"", "dump")
registerDeterminismTask("traceDiff", "First divergent step: --args=\"<left> <right> [--dump <file>]\"", "diff")
registerDeterminismTask(
    "determinismAudit",
    "Traces the 2D audit corpus (-PauditLabel, -PauditCompare, -PauditJvmArgs)",
    "audit",
) {
    val label = (findProperty("auditLabel") as String?) ?: "run"
    val compare = findProperty("auditCompare") as String?
    listOf(projectPath, determinismOutDir.get().dir(label).asFile.absolutePath) +
        if (compare == null) {
            emptyList()
        } else {
            listOf(
                "--compare",
                determinismOutDir.get().dir(compare).asFile.absolutePath,
            )
        }
}.configure {
    (findProperty("auditJvmArgs") as String?)?.let { jvmArgs(it.split(" ")) }
}
registerDeterminismTask("determinismParity", "Checks that must match on every architecture", "parity") {
    listOf(projectPath, "blocking", determinismOutDir.get().dir("parity").asFile.absolutePath)
}
registerDeterminismTask("determinismGate", "Reports evidence blockers for the 3D tunnel physics gate", "gate") {
    listOf(projectPath)
}
registerDeterminismTask("determinismParity2d", "Compares 2D traces with the committed x86-64 references", "parity") {
    listOf(projectPath, "2d", determinismOutDir.get().dir("parity").asFile.absolutePath)
}
registerDeterminismTask(
    "determinismUpdateReferences",
    "Rewrites committed hash vectors and 2D reference traces",
    "update-references",
) {
    listOf(projectPath)
}
tasks.register<JavaExec>("updateTunnelGoldens") {
    group = "verification"
    description = "Compares or provenance-guards updates to tunnel golden candidates"
    val updaterArgs = (findProperty("tunnelGoldenArgs") as String?)?.trim()?.split(Regex("\\s+")) ?: emptyList()
    if (!updaterArgs.contains("--bootstrap")) dependsOn("test")
    mainClass.set("ru.rkhamatyarov.tunnel.TunnelGoldenUpdater")
    classpath = mainSourceSet.get().runtimeClasspath
    systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
    args(listOf("--generate") + updaterArgs)
}

val frontendDir = layout.projectDirectory.dir("src/main/resources/META-INF/resources")
val frontendDistDir = frontendDir.dir("dist")
val npmCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

tasks.register<Exec>("buildFrontend") {
    workingDir = frontendDir.asFile
    commandLine(npmCommand, "run", "build")
    dependsOn("installFrontend")

    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("package-lock.json"),
        frontendDir.file("vite.config.ts"),
        frontendDir.file("tsconfig.json"),
        frontendDir.file("tsconfig.node.json"),
    )
    inputs.dir(frontendDir.dir("src"))
    outputs.dir(frontendDistDir)
}

tasks.register<Exec>("installFrontend") {
    workingDir = frontendDir.asFile
    commandLine(npmCommand, "ci")

    inputs.files(
        frontendDir.file("package.json"),
        frontendDir.file("package-lock.json"),
    )
    outputs.dir(frontendDir.dir("node_modules"))
}

val testFrontend =
    tasks.register<Exec>("testFrontend") {
        group = "verification"
        description = "Runs frontend protocol regression tests"
        workingDir = frontendDir.asFile
        commandLine(npmCommand, "test")
        dependsOn("installFrontend")
    }

tasks.named("check") {
    dependsOn(testFrontend)
}

tasks.register<Sync>("copyFrontend") {
    dependsOn("buildFrontend", "processResources")
    from(frontendDistDir)
    into(layout.buildDirectory.dir("resources/main/META-INF/resources"))
}

tasks.processResources {
    mustRunAfter("installFrontend", "buildFrontend")
    exclude { details ->
        val rel = details.relativePath.pathString.replace('\\', '/')
        val tail = rel.removePrefix("META-INF/resources/")
        tail.startsWith("dist/") ||
            tail.startsWith("node_modules/") ||
            tail.startsWith("test/") ||
            tail.startsWith("src/") ||
            tail == "index.html" ||
            tail == "package.json" ||
            tail == "package-lock.json" ||
            tail == "tsconfig.json" ||
            tail == "tsconfig.node.json" ||
            tail == "vite.config.ts"
    }
}

tasks.classes {
    dependsOn("copyFrontend")
}

val nativeBuildTaskNames = setOf("nativeBuild", "nativeCompile")
val nativeBuildRequested = gradle.startParameter.taskNames.any { it in nativeBuildTaskNames }

if (nativeBuildRequested) {
    System.setProperty("quarkus.package.type", "native")
    tasks.named("build").configure { mustRunAfter("clean") }
}

tasks.register("nativeCompile") {
    group = "build"
    description = "Cleans and builds a native executable"
    dependsOn("clean", "build")
}

tasks.register("nativeBuild") {
    group = "build"
    description = "Alias for nativeCompile"
    dependsOn("nativeCompile")
}
