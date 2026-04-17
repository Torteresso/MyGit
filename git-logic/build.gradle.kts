plugins {
    kotlin("jvm")
}

dependencies {
    // For INI-configuration
    implementation(libs.commons.configuration2)

    //JGit
    implementation(libs.org.eclipse.jgit)
}
