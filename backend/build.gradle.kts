import java.util.concurrent.ConcurrentHashMap

plugins {
    java
    id("org.springframework.boot") version "3.3.5" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.aegis"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

val moduleTestResults = ConcurrentHashMap<String, Triple<Int, Int, Int>>()

subprojects {
    apply(plugin = "java")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        systemProperty("AEGIS_ALLOW_NO_DOCKER", System.getenv("AEGIS_ALLOW_NO_DOCKER") ?: System.getProperty("AEGIS_ALLOW_NO_DOCKER") ?: "")
        if (System.getenv("AEGIS_ALLOW_NO_DOCKER") != null) {
            environment("AEGIS_ALLOW_NO_DOCKER", System.getenv("AEGIS_ALLOW_NO_DOCKER")!!)
        }
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = true
        }

        afterSuite(KotlinClosure2<TestDescriptor, TestResult, Unit>({ desc, result ->
            if (desc.parent == null) {
                val passed = result.successfulTestCount.toInt()
                val skipped = result.skippedTestCount.toInt()
                val failed = result.failedTestCount.toInt()
                moduleTestResults[project.name] = Triple(passed, skipped, failed)
                println("[MODULE TEST SUMMARY] ${project.name}: $passed passed, $skipped skipped, $failed failed (${result.testCount} total)")
            }
        }))
    }

    dependencies {
        val testImplementation by configurations
        val testRuntimeOnly by configurations

        testImplementation(platform("org.junit:junit-bom:5.10.3"))
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.assertj:assertj-core:3.26.3")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }
}

gradle.buildFinished {
    if (moduleTestResults.isNotEmpty()) {
        println()
        println("================================================================================")
        println("PROJECT AEGIS - PER-MODULE TEST SUMMARY")
        println("================================================================================")
        var totalPassed = 0
        var totalSkipped = 0
        var totalFailed = 0
        for (entry in moduleTestResults.toSortedMap().entries) {
            val module = entry.key
            val counts = entry.value
            val passed = counts.first
            val skipped = counts.second
            val failed = counts.third
            totalPassed += passed
            totalSkipped += skipped
            totalFailed += failed
            println(String.format("  %-25s -> Passed: %-3d  Skipped: %-3d  Failed: %-3d", module, passed, skipped, failed))
        }
        println("--------------------------------------------------------------------------------")
        println(String.format("  %-25s -> Passed: %-3d  Skipped: %-3d  Failed: %-3d", "TOTAL", totalPassed, totalSkipped, totalFailed))
        println("================================================================================")
        println()
    }
}
