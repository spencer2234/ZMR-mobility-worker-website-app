// Top-level build file. Plugin versions live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    // Not applied anywhere (AGP 9 has Kotlin support built in); declared here only to pin the Kotlin version.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
