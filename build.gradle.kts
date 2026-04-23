import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.allopen") version "2.3.20"
    id("io.quarkus") version "3.34.5"
    id("com.google.protobuf") version "0.9.6"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.diffplug.spotless") version "8.4.0"
}

group = "ru.rkhamatyarov"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(enforcedPlatform("io.quarkus:quarkus-bom:3.34.5"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.quarkus:quarkus-arc")
    implementation("io.quarkus:quarkus-kotlin")
    implementation("io.quarkus:quarkus-rest")
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-websockets-next")
    implementation("io.quarkus:quarkus-jackson")
    implementation("io.quarkus:quarkus-scheduler")
    implementation("io.quarkus:quarkus-logging-json")

    implementation("io.quarkiverse.quinoa:quarkus-quinoa:2.8.1")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.protobuf:protobuf-java")
    implementation("com.google.protobuf:protobuf-kotlin")

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

tasks.register<Copy>("copyFrontend") {
    dependsOn("buildFrontend", "processResources")
    from(frontendDistDir)
    into(layout.buildDirectory.dir("resources/main/META-INF/resources"))
}

tasks.processResources {
    exclude(
        "META-INF/resources/assets/**",
        "META-INF/resources/dist/**",
        "META-INF/resources/index.html",
        "META-INF/resources/node_modules/**",
        "META-INF/resources/package.json",
        "META-INF/resources/package-lock.json",
        "META-INF/resources/src/**",
        "META-INF/resources/tsconfig.json",
        "META-INF/resources/tsconfig.node.json",
        "META-INF/resources/vite.config.ts",
    )
}

tasks.classes {
    dependsOn("copyFrontend")
}
