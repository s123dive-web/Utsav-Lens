plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.utsavlens.platform"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests { isIncludeAndroidResources = true } }
}


dependencies {
    api(project(":core"))
    implementation(libs.coroutines.android)
    implementation(libs.exifinterface)
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
