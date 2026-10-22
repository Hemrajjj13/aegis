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

    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
}
