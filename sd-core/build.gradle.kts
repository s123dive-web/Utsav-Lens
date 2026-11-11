import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    api(project(":core"))
    // Same Java API as onnxruntime-android; the Android module supplies the real runtime.
    compileOnly(libs.onnxruntime.jvm)
    testImplementation(libs.onnxruntime.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
}

tasks.test {
    // Real-weights validation (see .github/workflows/local-ai-validate.yml); skipped when unset.
    systemProperty("sd.modelDir", providers.gradleProperty("sdModelDir").orNull ?: "")
    systemProperty("sd.outDir", layout.buildDirectory.dir("sd-out").get().asFile.absolutePath)
    maxHeapSize = "3g"
    testLogging { showStandardStreams = true }
}
