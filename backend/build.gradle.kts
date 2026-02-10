import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    kotlin("plugin.jpa") version "2.1.0"
}

group = "com.tinto"
version = "0.1.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    // Shibboleth repository for OpenSAML dependencies (required by WSS4J)
    maven {
        url = uri("https://build.shibboleth.net/maven/releases/")
    }
}

dependencies {
    // Fix Guava transitive dependency issues
    implementation(platform("com.google.guava:guava-bom:33.4.0-jre"))
    
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-web-services") // For SOAP client
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    
    // Database
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.springframework.boot:spring-boot-flyway") // Spring Boot 4 Flyway autoconfiguration
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    
    // SOAP & XML Processing
    implementation("org.apache.cxf:cxf-spring-boot-starter-jaxws:4.0.5") // Apache CXF for SOAP
    implementation("org.apache.cxf:cxf-rt-ws-security:4.0.5") // WS-Security support
    implementation("org.apache.cxf:cxf-rt-features-logging:4.0.5") // Logging feature for CXF
    implementation("javax.xml.soap:javax.xml.soap-api:1.4.0")
    implementation("com.sun.xml.messaging.saaj:saaj-impl:3.0.4")
    
    // XML Digital Signatures (XAdES-EPES)
    implementation("org.apache.santuario:xmlsec:3.0.4") // Apache XMLSec for XML signing
    implementation("xalan:xalan:2.7.3") // XML transformation
    
    // Encryption & Security
    implementation("com.github.ulisesbocchio:jasypt-spring-boot-starter:3.0.5") // Jasypt for encrypted properties
    implementation("org.bouncycastle:bcprov-jdk18on:1.79") // BouncyCastle for advanced crypto
    implementation("org.bouncycastle:bcpkix-jdk18on:1.79") // BouncyCastle PKIX for X.509
    
    // Utilities
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("commons-codec:commons-codec:1.17.2")
    
    // Development
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.14") // MockK for Kotlin
    testImplementation("com.ninja-squad:springmockk:4.0.2") // Spring + MockK integration
    testImplementation("org.testcontainers:testcontainers:1.20.4") // Testcontainers for E2E
    testImplementation("org.testcontainers:postgresql:1.20.4")
    testImplementation("org.testcontainers:junit-jupiter:1.20.4")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Run E2E tests against real DIAN Habilitación environment
    systemProperty("dian.environment", System.getProperty("dian.environment", "habilitacion"))
}
