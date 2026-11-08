import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.net.URI
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// MQTT credentials and endpoints live outside version control, like local/signing.properties.
// providers.fileContents() makes the configuration cache invalidate when the file changes.
val mqttFile = providers.fileContents(
    rootProject.layout.projectDirectory.file("local/mqtt.properties"),
).asText
val mqttValues = Properties().apply {
    runCatching { mqttFile.orNull?.let { load(StringReader(it)) } }
}

fun mqttValue(name: String, fallback: String = ""): String =
    providers.gradleProperty(name).orNull
        ?: mqttValues.getProperty(name)
        ?: System.getenv(name)
        ?: fallback

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The guard-device API. Keep the dev origin out of version control:
// ./gradlew -PtvApiBase=http://<dev-host>:8080/api/v1 assembleStandardDebug
// Public builds have no default backend. Provide one only when the build needs remote mode:
// ./gradlew -PtvApiBase=https://<your-api-host>/api/v1 assembleStandardRelease
val tvApiBase = (project.findProperty("tvApiBase") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: ""

// The private release channels pass -PtvOfficialBuild=true. The community build leaves it
// false, so the official product name never has to be hard-coded in shared source.
val tvOfficialBuild = (project.findProperty("tvOfficialBuild") as String?)
    ?.equals("true", ignoreCase = true) == true

val tvMqttUrl = mqttValue("tvMqttUrl")
val tvMqttTopicPrefix = mqttValue("tvMqttTopicPrefix")
val tvMqttClientPrefix = mqttValue("tvMqttClientPrefix", "tv-app-")

fun requireMqtt(condition: Boolean, message: String) {
    if (!condition) throw GradleException(message)
}

/** True for loopback and RFC 1918 IPv4 hosts, where dev may split API and broker. */
fun isPrivateHost(host: String): Boolean {
    if (host == "localhost" || host == "::1" || host.endsWith(".local")) return true
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 10 ||
        parts[0] == 127 ||
        (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 192 && parts[1] == 168)
}

// Fail the build instead of shipping an APK whose MQTT is rejected at runtime:
// PolicyMqttClient refuses a plaintext broker when the API is HTTPS, which silently
// degrades to HTTP polling. Keep only environment-independent invariants here; the
// private build supplies the exact host, port and topic prefix through
// local/mqtt.properties (or -P). This keeps the public source free of official hosts
// while still catching an API/broker environment mix-up.
if (tvApiBase.isNotBlank()) {
    if (tvApiBase.startsWith("https://") && tvMqttUrl.isNotBlank()) {
        requireMqtt(
            tvMqttUrl.startsWith("ssl://"),
            "HTTPS API $tvApiBase requires an ssl:// MQTT URL (got '$tvMqttUrl')"
        )
    }
    val apiHost = runCatching { URI(tvApiBase).host }.getOrNull()
    val mqttHost = runCatching { URI(tvMqttUrl).host }.getOrNull()
    if (!apiHost.isNullOrEmpty() && !mqttHost.isNullOrEmpty()) {
        // Dev intentionally keeps the API on the build machine and the broker on another
        // LAN server; public (production/preview) endpoints still have to match exactly.
        val lanPair = isPrivateHost(apiHost) && isPrivateHost(mqttHost)
        requireMqtt(
            apiHost == mqttHost || lanPair,
            "API host '$apiHost' and MQTT host '$mqttHost' must belong to the same environment"
        )
    }
    // The prefix is concatenated verbatim with "command/device/...": dev uses a
    // "dev-<host>-" namespace, preview uses "preview/", pro stays empty. Require a
    // delimiter so a typo cannot merge into another environment's topic name.
    requireMqtt(
        tvMqttTopicPrefix.isEmpty() ||
            tvMqttTopicPrefix.endsWith("/") ||
            tvMqttTopicPrefix.endsWith("-"),
        "MQTT topic prefix must be empty or end with '/' or '-' (got '$tvMqttTopicPrefix')"
    )
}

android {
    // Keep Aerial's source namespace so upstream merges stay small. The installed identity is
    // PlanYourTV's own applicationId, which lets the community build coexist with the official app.
    namespace = "app.orchard.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.planyourtv.tv"
        // Android 5.0. Compose 1.10 and later need 6.0, so the Compose BOM below stays on 1.9.
        minSdk = 21
        targetSdk = 34
        // Product version: Aerial's version stays the prefix (v1.85 / 116 -> 1.85.000 / 116000).
        // On every upstream merge update these two from the Aerial tag and reset the revision to 0.
        // Raise productRevision for each product release; never reuse or decrease it.
        val aerialVersionName = "1.85"
        val aerialVersionCode = 116
        val productRevision = 1
        require(productRevision in 0..999) { "productRevision must fit in three digits" }
        versionCode = aerialVersionCode * 1000 + productRevision
        versionName = "$aerialVersionName.${productRevision.toString().padStart(3, '0')}"
        // `./gradlew -Pdiag ...`: a build for one reporter's TV whose logcat shows no app lines;
        // see system/DiagLog.kt.
        val diag = project.hasProperty("diag")
        buildConfigField("boolean", "DIAG", diag.toString())
        if (diag) versionNameSuffix = "-diag"
        buildConfigField("String", "TV_API_BASE", "\"$tvApiBase\"")
        // The community build has no backend baked in: keep the cloud/remote UI hidden and do
        // not install the HTTP/MQTT adapters. An official build opts in with -PtvApiBase.
        buildConfigField("boolean", "CLOUD_ENABLED", tvApiBase.isNotBlank().toString())
        // MQTT command channel. Empty URL disables it; config/usage continue over HTTP.
        // Credentials are fetched per device at runtime from /api/v2 and are never baked in.
        buildConfigField("String", "TV_MQTT_URL", buildConfigString(tvMqttUrl))
        buildConfigField("String", "TV_MQTT_TOPIC_PREFIX", buildConfigString(tvMqttTopicPrefix))
        buildConfigField("String", "TV_MQTT_CLIENT_PREFIX", buildConfigString(tvMqttClientPrefix))
        buildConfigField("boolean", "OFFICIAL_BUILD", tvOfficialBuild.toString())
    }

    // A call newer than minSdk fails the release build (lintVital runs with assembleRelease):
    // 1.74 shipped Context.getSystemService(Class), Android 6+, in the guard's onServiceConnected,
    // and the guard crashed on every bind on Android 5 (issues #1010 #1012 #1017 #1018).
    lint {
        fatal += "NewApi"
    }

    // standard: a normal HOME app. guard: no HOME intent filter at all, for China-market firmwares
    // (some TCL / Hisense builds) that refuse to install any package declaring CATEGORY_HOME. The guard
    // build takes over Home through its accessibility service instead.
    flavorDimensions += "home"
    productFlavors {
        create("standard") { dimension = "home" }
        create("guard") {
            dimension = "home"
            versionNameSuffix = "-guard"
        }
    }

    // A build baked with a plain-HTTP dev API must allow cleartext, or Android 9+ rejects the
    // request before it leaves the TV. Production builds stay HTTPS-only (main config).
    sourceSets.getByName("debug").res.srcDir("src/devCleartext/res")
    if (tvApiBase.startsWith("http://")) {
        sourceSets.getByName("release").res.srcDir("src/devCleartext/res")
    }

    // The TVs only accept updates signed with the key Aerial was first installed with. That key
    // lives outside version control in local/ (see docs/HANDOFF.md); without it the build falls
    // back to this machine's debug key, which installs fresh but cannot update existing copies.
    val signingProps = rootProject.file("local/signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("aerial") {
                storeFile = rootProject.file("local/" + p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("aerial") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        // java.time and friends on Android 5.x to 7.x.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
    // ExoPlayer seeks inside the bundled quiz intro; a compressed asset cannot
    // be opened as a random-access MP4 on Android 5/6.
    androidResources {
        noCompress += "mp4"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Product policy hooks. The module is a no-op until a policy is installed.
    implementation(project(":policy"))
    // MQTT command channel for early-stop/cancel; config and usage stay on HTTP.
    implementation("org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Compose 1.9.5, the last release that runs on Android 5.0 (1.10 raised minSdk to 23).
    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.core:core-ktx:1.16.0")
    // Baseline Profile install (src/main/baseline-prof.txt) and its compile state in the diagnostics.
    implementation("androidx.profileinstaller:profileinstaller:1.4.0")
    // Aerial films. 1.8 is the last Media3 that runs on Android 5.0 (1.9 raised minSdk to 23).
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-datasource:1.8.1")
    implementation("androidx.media3:media3-database:1.8.1")
    // Phone setup page (a tiny LAN web server) and the QR code that opens it.
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
}

/**
 * Release builds carry their own dex metadata (assets/aerial-dm/28.dm and 31.dm): the in-app update
 * writes the matching one into its install session beside the APK, and the system then compiles
 * Aerial with its Baseline Profile as it installs. System installers ignore the profile inside the
 * APK (assets/dexopt), and the background job that would compile it later needs an idle device,
 * which a TV that is on never is (Xiaomi MiTV-MFTR0: not once in 8 hours), so without this an
 * updated Aerial ran uncompiled until the next update (docs/PERF-2026-10-30.md).
 *
 * AGP's dex metadata maps API levels to files in dex-metadata-map.properties (28-30: profile format
 * 010, 31 and up: 015; the wrong one is ignored with "Profile version mismatch"). Xiaomi's Android
 * 14 refuses a .dm without manifest.json (INSTALL_FAILED_BAD_DEX_METADATA), so one is added.
 */
abstract class DexMetadataAssets : DefaultTask() {
    @get:InputDirectory abstract val dmDir: DirectoryProperty
    @get:Input abstract val packageName: Property<String>
    @get:Input abstract val versionCode: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = dmDir.get().asFile
        val map = Properties().apply { File(dir, "dex-metadata-map.properties").inputStream().use { load(it) } }
        val out = File(outputDir.get().asFile, "aerial-dm").apply { deleteRecursively(); mkdirs() }
        val manifest = """{"packageName":"${packageName.get()}","versionCode":${versionCode.get()}}"""
        for (api in listOf("28", "31")) {
            val source = File(dir, map.getProperty(api) ?: error("no dex metadata for API $api in $dir"))
            ZipOutputStream(File(out, "$api.dm").outputStream()).use { zip ->
                ZipFile(source).use { from ->
                    for (entry in from.entries()) {
                        if (entry.name == "manifest.json") continue
                        zip.putNextEntry(ZipEntry(entry.name))
                        from.getInputStream(entry).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
            }
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val task = tasks.register<DexMetadataAssets>("${variant.name}DexMetadataAssets") {
            dependsOn("compile${name}ArtProfile")
            dmDir.set(layout.buildDirectory.dir("intermediates/dex_metadata_directory/${variant.name}/compile${name}ArtProfile"))
            packageName.set(variant.applicationId)
            versionCode.set(variant.outputs.single().versionCode.map { it ?: 0 })
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, DexMetadataAssets::outputDir)
    }
}
