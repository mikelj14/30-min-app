plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // runs Room's code generator at build time
    alias(libs.plugins.room)
}

// Release version comes from the git tag: CI passes -PappVersion=1.2.3 for tag v1.2.3.
// versionCode = major*10000 + minor*100 + patch, so it always grows with the tag
// (Android refuses to install a lower versionCode over a higher one).
val appVersion: String? = providers.gradleProperty("appVersion").orNull
val appVersionName: String = appVersion ?: "0.1.0-dev" // local and PR builds
val appVersionCode: Int =
    if (appVersion == null) {
        1
    } else {
        val n: List<Int> =
            Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)
                ?.groupValues?.drop(1)?.map { it.toInt() }
                ?.takeIf { it[1] < 100 && it[2] < 100 }
                ?: error(
                    "appVersion must be MAJOR.MINOR.PATCH, minor and patch < 100: '$appVersion'"
                )
        n[0] * 10_000 + n[1] * 100 + n[2]
    }

// Signing secrets come from environment variables (GitHub secrets in CI).
// Absent locally and on PRs: the release build is simply left unsigned.
val keystoreFile = providers.environmentVariable("RELEASE_KEYSTORE_FILE").orNull

android {
    namespace = "dev.thirtymin.app"
    // compileSdk = APIs visible at compile time (Compose 1.12 needs 37).
    // targetSdk = runtime behavior opt-in; bump separately after testing.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.thirtymin.app"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        // Runs androidTest/ (on-device tests, e.g. Room DAO against real SQLite).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = file(keystoreFile)
                storePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = storePassword // PKCS12: one password for store and key
            }
        }
    }

    buildTypes {
        release {
            if (keystoreFile != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            // Drops resources no code references (after R8 removed the code).
            isShrinkResources = true
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
