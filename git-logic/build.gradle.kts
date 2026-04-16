plugins {
    kotlin("jvm")
}
repositories {
    mavenCentral()
}
dependencies {
    // For INI-configuration
    implementation("org.apache.commons:commons-configuration2:2.13.0")

    //JGit
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.6.0.202603022253-r")
}
