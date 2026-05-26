import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.allopen") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("io.quarkus") version "3.36.0"
    id("com.google.protobuf") version "0.10.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.diffplug.spotless") version "8.5.1"
}

group = "ru.rkhamatyarov"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus:quarkus-bom:3.35.3"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-config-yaml")
    implementation("io.quarkus:quarkus-logging-json")

    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.protobuf:protobuf-kotlin")

    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.quarkus:quarkus-junit5-mockito")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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
        artifact = "com.google.protobuf:protoc:4.33.2"
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

ktlint {
    version.set("1.2.1")
    filter {
        exclude { element ->
            element.file.path.contains("build/generated")
        }
    }
}

spotless {
    kotlin {
        ktlint()
        target("**/*.kt")
        targetExclude("build/generated/**/*.kt")
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

if ("nativeBuild" in gradle.startParameter.taskNames) {
    System.setProperty("quarkus.package.type", "native")
    tasks.named("build").configure { mustRunAfter("clean") }
}

tasks.register("nativeBuild") {
    group = "build"
    description = "Cleans and builds a native executable (equivalent to: clean build -Dquarkus.package.type=native)"
    dependsOn("clean", "build")
}
