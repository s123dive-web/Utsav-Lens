plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.utsavlens.data"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // Exported Room schemas double as test assets for MigrationTestHelper.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

room { schemaDirectory("$projectDir/schemas") }

dependencies {
    api(project(":core"))
    implementation(libs.coroutines.android)
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
