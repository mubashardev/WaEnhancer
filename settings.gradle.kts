pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "Wa Enhancer X"
include(":app")
include(":api")

// settings.gradle.kts computes hasProSources below. Since Gradle script evaluation is top-down,
// we should declare hasProSources earlier, or just check the directory existence directly.
val hasProSources: Boolean = if (providers.gradleProperty("hasProSourcesOverride").isPresent) {
    providers.gradleProperty("hasProSourcesOverride").get().toBoolean()
} else {
    val proDir = file("pro")
    proDir.exists() &&
        proDir.walkTopDown()
            .filter { it.isFile }
            .any { file ->
                file.name !in setOf(".gitignore", "README.md") &&
                file.name != ".git" &&
                !file.name.endsWith(".git")
            }
}

if (hasProSources) {
    include(":pro")
    project(":pro").projectDir = file("pro")
}

// ─────────────────────────────────────────────────────────────────────────────
// Private submodule detection
// The private repo lives at pro (git submodule).
// If the submodule is populated (has actual source files), we expose it to the
// app build script via the "hasProSources" extra property so it can wire up
// the additional source set.  Forks / CI runs without access to the private
// repo will simply build the open-source variant — no errors, no stubs needed.
// ─────────────────────────────────────────────────────────────────────────────

gradle.extra["hasProSources"] = hasProSources
gradle.rootProject {
    extra["hasProSources"] = hasProSources
}
if (hasProSources) {
    logger.lifecycle("WaEnhancerX: Private pro submodule detected — building with pro features.")
} else {
    logger.lifecycle("WaEnhancerX: Private pro submodule not found — building open-source variant.")
}
