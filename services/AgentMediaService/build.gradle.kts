plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.agentos.media"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.agentos.media"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    sourceSets.getByName("main") {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src")
    }
    sourceSets.getByName("test").java.srcDirs("test")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":libraries:CapabilityApi"))
    testImplementation("junit:junit:4.13.2")
}
