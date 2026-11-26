import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

// A phone shell for the Parent Center page served by the TV over the LAN. It is
// deliberately a separate application: the TV app stays the only thing that is
// installed, guarded and released; this shell needs INTERNET and CAMERA, plus
// the install permission for its own optional update.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.orchard.tv.parent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.planyourtv.parent"
        // The camera library supports API 19; the shell keeps a modern WebView
        // requirement so the TV page's mobile layout and its storage APIs work.
        minSdk = 24
        targetSdk = 34
        // The shell is versioned independently from the TV app: the web page and
        // the TV server define the contract, not the APK version.
        versionCode = 11
        versionName = "1.2.5"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // The phone shell has its own release identity. Sharing the TV app's key
    // would make one leaked key affect both applications, so official builds
    // read local/parent-signing.properties instead. Without it, local release
    // builds fall back to the debug key and must not be distributed.
    // providers.fileContents() makes the configuration cache invalidate when the
    // file appears or changes; a plain exists() check can reuse a cached debug
    // configuration after the signing file has been uploaded to a warm workspace.
    val signingProps = providers.fileContents(
        rootProject.layout.projectDirectory.file("local/parent-signing.properties"),
    ).asText
    val signingValues = Properties().apply {
        runCatching { signingProps.orNull?.let { load(StringReader(it)) } }
    }
    val signingStore = signingValues.getProperty("storeFile")
    signingConfigs {
        if (signingStore != null) {
            create("release") {
                storeFile = rootProject.file("local/$signingStore")
                storePassword = signingValues.getProperty("storePassword")
                keyAlias = signingValues.getProperty("keyAlias")
                keyPassword = signingValues.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            ndk {
                // zxing-cpp ships x86/x86_64 libraries for emulators. The phone
                // app is distributed as one APK; drop the emulator ABIs so the
                // native decoder costs about 2.8 MB instead of 6.2 MB.
                abiFilters += listOf("armeabi-v7a", "arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        fatal += "NewApi"
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // AppCompat keeps the shell usable on Android 7-9 phones. CameraX owns the
    // camera preview, zoom and analysis with the Camera2 stack; zxing-cpp is the
    // native ZXing decoder. It reads the YUV Y plane and rotation directly, so
    // the analyzer no longer copies and rotates every frame in Kotlin. Java ZXing
    // was the bottleneck: it decoded at most every 100 ms and could not keep up
    // with the continuous frames that make the phone browser scan so quickly.
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Photo picker contracts (activity 1.7+) open media uploads in the system
    // gallery instead of the document browser.
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("io.github.zxing-cpp:android:2.3.0")
    testImplementation("junit:junit:4.13.2")
    // android.jar's org.json methods are stubs in local unit tests.
    testImplementation("org.json:json:20231013")
}
