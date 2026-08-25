plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.agentos.capability.core"
    compileSdk = 35

    defaultConfig { minSdk = 29 }
    sourceSets.getByName("main").java.srcDirs("src")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
