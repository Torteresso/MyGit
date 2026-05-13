plugins {
    kotlin("jvm")

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

dependencies {
    //Git-logic
    implementation(project(":git-logic"))

    // Clikt
    implementation(libs.clikt)
    implementation(libs.clikt.markdown)

    // Tests
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    implementation(libs.slf4j.nop)
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}


application {
    // Define the main class for the application.
    mainClass = "MyGitKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
    applicationName = "mgit-dist"
}


