pluginManagement {
    repositories {
        // Aerial's upstream build assumes direct access to Google/Maven Central.
        // The Aliyun mirrors keep this fork buildable on a China-network machine.
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    // The plugin marker artifacts are tiny POMs that point at the real plugin.
    // Mapping them here keeps an offline/cached build working when the marker
    // itself is not in the local Gradle cache.
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application", "com.android.library" ->
                    useModule("com.android.tools.build:gradle:${requested.version}")
                "org.jetbrains.kotlin.android" ->
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
                "org.jetbrains.kotlin.plugin.compose" ->
                    useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
    }
}
rootProject.name = "PlanYourTV"
include(":app")
// Optional phone shell that opens the TV's local Parent Center page. It is a
// separate application; it does not share code or permissions with the TV app.
include(":parent")
// The public source snapshot ships only the Android 5.0+ app; the private tree keeps Lite too.
if (file("lite").isDirectory) include(":lite")
// Product logic. Kept out of :app so upstream Aerial merges stay small.
include(":policy")
