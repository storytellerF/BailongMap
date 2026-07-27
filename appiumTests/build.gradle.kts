plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.appium.java.client)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.slf4j.simple)
}

val appiumTags = providers.gradleProperty("appiumTags")
val shouldRunAppium =
    providers.gradleProperty("runAppium").map { it == "true" }.getOrElse(false) ||
        appiumTags.isPresent

tasks.test {
    useJUnitPlatform {
        appiumTags.orNull
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?.let { includeTags(*it.toTypedArray()) }
    }
    enabled = shouldRunAppium
    maxParallelForks = 1
    if (shouldRunAppium) {
        dependsOn(":app:androidApp:installDebug")
    }
    systemProperty("project.root.dir", rootProject.projectDir.absolutePath)
}
