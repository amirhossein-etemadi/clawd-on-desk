// Ilia Companion — Android app (floating overlay pet + live desktop sync).
// See android/README.md for build/sideload instructions.
//
// Using the classic buildscript/classpath style here instead of the modern
// plugins{} DSL with a version -- the plugins{} DSL looks up a separate
// "plugin marker" artifact that isn't reliably resolvable for every AGP
// release, while classpath references the real com.android.tools.build:gradle
// artifact directly (confirmed present on Google's Maven repo). app/build.gradle.kts
// applies the plugins with no version (inherited from this classpath).

// Google 404s Android developer content (dl.google.com/dl/android/maven2)
// for some regions/IP ranges, so mirrors of the Google Maven repo are listed
// first; google() stays as a fallback for networks where it works.
buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
    }
}

allprojects {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
    }
}
