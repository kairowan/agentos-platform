plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.agentos.capability"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agentos.capability"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.3.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("AndroidManifest.xml")
            java.srcDirs("src")
        }
        getByName("test").java.srcDirs("test")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":libraries:CapabilityApi"))
    implementation(project(":libraries:CapabilityCore"))
    testImplementation("junit:junit:4.13.2")
}
