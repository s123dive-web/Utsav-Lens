// Plugins are put on the root buildscript classpath (not the plugins {} DSL) so that the
// pure-Kotlin modules can be built with -PjvmOnly on machines without Google Maven access.
buildscript {
    val jvmOnly = providers.gradleProperty("jvmOnly").isPresent
    repositories {
        if (!jvmOnly) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        val kotlin = libs.versions.kotlin.get()
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlin")
        classpath("org.jetbrains.kotlin:kotlin-serialization:$kotlin")
        if (!jvmOnly) {
            classpath("com.android.tools.build:gradle:${libs.versions.agp.get()}")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:$kotlin")
            classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:${libs.versions.ksp.get()}")
            classpath("com.google.dagger:hilt-android-gradle-plugin:${libs.versions.hilt.get()}")
            classpath("androidx.room:room-gradle-plugin:${libs.versions.room.get()}")
        }
    }
}
