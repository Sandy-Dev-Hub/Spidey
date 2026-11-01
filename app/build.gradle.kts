import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Read secrets from local.properties (gitignored – never committed)
val localProps = Properties().also { props ->
    val f = rootProject.file("local.properties")
    if (f.exists()) props.load(f.inputStream())
}

android {
    namespace = "com.spidey.spikes"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.spidey.spikes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-spike"

        // Picovoice AccessKey injected at build time from local.properties:
        //   picovoice.accessKey=YOUR_KEY_HERE
        // Empty string until the user supplies the key.
        buildConfigField(
            "String",
            "PICOVOICE_ACCESS_KEY",
            "\"${localProps.getProperty("picovoice.accessKey", "")}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // Distinct app ID so it can coexist with a future release build
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = false
        }
    }

    // Java 17 source/target so the compiler produces Java-17 bytecode
    // even though the JDK running Gradle is version 25.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true  // exposes BuildConfig.PICOVOICE_ACCESS_KEY
    }
}

dependencies {
    // ---- Compose BOM (pins all compose-* versions consistently) --------
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    // ---- Core -------------------------------------------------------
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // ---- Compose UI -------------------------------------------------
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ---- Navigation -------------------------------------------------
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // ---- LocalBroadcastManager (service → UI updates) ---------------
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // ---- Vosk Offline Speech Recognition & Keyword Spotting ----------
    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    // ---- Room (Contact Aliases & Caching) ---------------------------
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ---- Testing ---------------------------------------------------
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")

    // ---- Debug only -------------------------------------------------
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
