// Top-level build file. Add project-wide configuration here.
// AGP 8.7.3 requires Gradle 8.9+ and works with JDK 17 source/target on JDK 25 runtime.
// Kotlin 2.0.21 is the last stable 2.0.x release; uses separate Compose compiler plugin.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
