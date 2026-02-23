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
}


application {
    // Define the main class for the application.
    mainClass = "MyGitKt"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}


