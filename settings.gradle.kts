// -PjvmOnly builds just the pure-Kotlin modules (no Android SDK / Google Maven needed).
val jvmOnly = providers.gradleProperty("jvmOnly").isPresent

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (!jvmOnly) google()
        mavenCentral()
    }
}

rootProject.name = "UtsavLens"

include(":core", ":themes", ":engine-api", ":sd-core")
if (!jvmOnly) {
    include(":platform", ":segmentation", ":engine-lite", ":engine-local", ":engine-cloud", ":video", ":data", ":app")
}
