import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// A phone shell for the Parent Center page served by the TV over the LAN. It is
// deliberately a separate application: the TV app stays the only thing that is
// installed, guarded and released; this shell only needs INTERNET and CAMERA.
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
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        viewBinding = true
    }

    // The phone shell has its own release identity. Sharing the TV app's key
    // would make one leaked key affect both applications, so official builds
    // read local/parent-signing.properties instead. Without it, local release
    // builds fall back to the debug key and must not be distributed.
    val signingProps = rootProject.file("local/parent-signing.properties")
    signingConfigs {
        if (signingProps.exists()) {
            val p = Properties().apply { signingProps.inputStream().use { load(it) } }
            create("release") {
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
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    // AppCompat keeps the shell usable on Android 7-9 phones; ZXing embedded
    // owns the camera preview and the QR decoder. No network or analytics SDK.
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
}
