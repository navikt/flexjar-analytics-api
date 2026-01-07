import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("io.ktor.plugin") version "3.3.3"
    id("com.gradleup.shadow") version "9.3.1"
    id("com.github.ben-manes.versions") version "0.53.0"
    application
}

group = "no.nav.flexjar"
version = "1.0.0"

application {
    mainClass.set("no.nav.flexjar.ApplicationKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

val ktorVersion = "3.3.3"
val kotlinVersion = "2.3.0"
val logbackVersion = "1.5.24"
val logstashVersion = "9.0"
val postgresVersion = "42.7.8"
val hikariVersion = "7.0.2"
val flywayVersion = "11.20.0"
val kotestVersion = "6.0.7"
val mockkVersion = "1.14.7"
val testcontainersVersion = "1.21.4"
val tokenSupportVersion = "5.0.13"
val exposedVersion = "0.56.0"
val micrometerVersion = "1.16.1"

dependencies {
    // Ktor Server
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-server-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    
    // Ktor Client (for token validation)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

    // Resources
    implementation("io.ktor:ktor-server-resources:$ktorVersion")

    // Explorer / Metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerVersion")
    
    // Serialization
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    
    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    // Exposed
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashVersion")
    
    // Excel export
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    
    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.testcontainers:testcontainers:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("no.nav.security:mock-oauth2-server:3.0.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks {
    shadowJar {
        mergeServiceFiles {
            exclude("META-INF/services/org.flywaydb.core.extensibility.Plugin")
        }

        archiveFileName.set("app.jar")
        archiveClassifier.set("")
        archiveVersion.set("")
    }
}
