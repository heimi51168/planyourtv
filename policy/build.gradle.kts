import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.planyourtv.policy"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Product policy storage. Aerial's own SharedPreferences stay untouched.
    implementation("com.tencent:mmkv:1.3.16")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub on the JVM unit-test classpath. Use the reference
    // implementation so the policy JSON contract can be tested without a device.
    testImplementation("org.json:json:20231013")
}
