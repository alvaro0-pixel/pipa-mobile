// Top-level build.gradle.kts
buildscript {
    repositories {
        google()
        mavenCentral()  // <-- adicione esta linha
    }
    dependencies {
        val nav_version = "2.9.8"
        classpath("androidx.navigation:navigation-safe-args-gradle-plugin:$nav_version")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    id("com.google.gms.google-services") version "4.4.4" apply false
}