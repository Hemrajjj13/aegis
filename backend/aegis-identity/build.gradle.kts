plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.2")
    }
}

dependencies {
    api(project(":aegis-core"))
    api(project(":aegis-storage"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")

    // Auth & Crypto libraries per doc 19 / ADR-0011
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")
    implementation("com.warrenstrange:googleauth:1.5.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
