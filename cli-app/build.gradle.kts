plugins {
    kotlin("jvm")

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    mavenCentral()
}

dependencies {
    //Git-logic
    implementation(project(":git-logic"))

    // Clikt
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.1.0")

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
    applicationName = "mgit-dist"
}


