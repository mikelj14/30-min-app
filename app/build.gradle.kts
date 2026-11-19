plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // runs Room's code generator at build time
    alias(libs.plugins.room)
}

android {
    namespace = "dev.thirtymin.app"
    // compileSdk = APIs visible at compile time (Compose 1.12 needs 37).
    // targetSdk = runtime behavior opt-in; bump separately after testing.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.thirtymin.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // Runs androidTest/ (on-device tests, e.g. Room DAO against real SQLite).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG hides debug-only UI in release
    }
}

// Room writes each DB version's table layout here as JSON. Committed, so a
// schema change shows up in review and migrations can be tested against it.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
