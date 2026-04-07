plugins {
    kotlin("jvm") version "2.3.10"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ajalt.clikt:clikt:5.1.0")

    // optional support for rendering markdown in help messages
    implementation("com.github.ajalt.clikt:clikt-markdown:5.1.0")

    implementation("org.apache.commons:commons-configuration2:2.13.0")

    // Tests
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}


application {
    // Define the main class for the application.
    mainClass = "MyGitKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}


