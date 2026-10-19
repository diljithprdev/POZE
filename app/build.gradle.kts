import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is read from local.properties (git-ignored) or environment
// variables, so no key material or password ever lands in the repository.
// Without it, debug builds and an unsigned release build still work.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String, envVar: String): String? =
    localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
        ?: System.getenv(envVar)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("poze.storeFile", "POZE_STORE_FILE")
val releaseStorePassword = signingValue("poze.storePassword", "POZE_STORE_PASSWORD")
val releaseKeyAlias = signingValue("poze.keyAlias", "POZE_KEY_ALIAS")
val releaseKeyPassword = signingValue("poze.keyPassword", "POZE_KEY_PASSWORD")
val hasReleaseSigning = releaseStorePath != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null &&
        file(releaseStorePath).exists()

android {
    namespace = "com.poze.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.poze.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-ktx:1.8.2")
}
