pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.myket.ir")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "IliaCompanion"
include(":app")
