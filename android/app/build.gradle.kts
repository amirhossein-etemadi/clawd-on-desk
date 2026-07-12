plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.iliacompanion.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.iliacompanion.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing: see android/README.md for how to generate a local
    // keystore and point these at it (via ~/.gradle/gradle.properties, not
    // committed here). Falls back to an unsigned release build if unset,
    // which Gradle will still produce -- you just sign it yourself before
    // installing (or use the debug build for personal sideloading, which is
    // already signed with a Gradle-generated debug key).
    signingConfigs {
        create("release") {
            val storeFilePath = project.findProperty("ILIA_RELEASE_STORE_FILE") as String?
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = project.findProperty("ILIA_RELEASE_STORE_PASSWORD") as String?
                keyAlias = project.findProperty("ILIA_RELEASE_KEY_ALIAS") as String?
                keyPassword = project.findProperty("ILIA_RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (project.hasProperty("ILIA_RELEASE_STORE_FILE")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
