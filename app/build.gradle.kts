plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.danielsmola.photoshelf"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.danielsmola.photoshelf"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // The release key is NOT in the repo. GitHub Actions writes it from the
    // PHOTOSHELF_KEYSTORE_BASE64 / PHOTOSHELF_KEY_PASSWORD secrets, so every build
    // is signed the same way and installs over the last one.
    val keystorePath = System.getenv("PHOTOSHELF_KEYSTORE_FILE")
    val releasePassword = System.getenv("PHOTOSHELF_KEY_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && !releasePassword.isNullOrBlank()

    signingConfigs {
        if (hasReleaseKey) {
            create("shelf") {
                storeFile = file(keystorePath!!)
                storePassword = releasePassword
                keyAlias = "photoshelf"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the secret (e.g. a local Android Studio build) fall back to the debug key.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("shelf") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
