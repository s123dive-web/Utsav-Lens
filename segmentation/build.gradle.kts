plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.utsavlens.segmentation"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
    implementation(project(":platform"))
    implementation(libs.mediapipe.tasks.vision)
    api(project(":core"))
    implementation(libs.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
