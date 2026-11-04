plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.3.5")
    }
}

springBoot {
    mainClass.set("com.aegis.AegisApplication")
}

dependencies {
    implementation(project(":aegis-identity"))
    implementation(project(":aegis-decision-api"))
    implementation(project(":aegis-storage"))
    implementation(project(":aegis-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
