plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

import java.util.Base64
import java.util.Properties

// Reads an override from local.properties or gradle.properties (like the other
// Kinalm apps): modelUrl is the AI cleanup model download URL.
fun prop(name: String, def: String): String {
    val props = Properties()
    for (f in listOf(rootProject.file("local.properties"), rootProject.file("gradle.properties"))) {
        if (f.exists()) {
            f.inputStream().use { props.load(it) }
            val v = props.getProperty(name)
            if (!v.isNullOrBlank()) return v.trim().trim('"')
        }
    }
    return def
}

// Shared debug signing key (same keystore as Friday): stored as base64 text so every
// CI build and every local build is signed the same way — new APKs install over old ones.
val sharedDebugKeystore: File = layout.buildDirectory.file("shared-debug.keystore").get().asFile.also { f ->
    if (!f.exists()) {
        f.parentFile.mkdirs()
        f.writeBytes(Base64.getMimeDecoder().decode(file("debug.keystore.b64").readText()))
    }
}

android {
    namespace = "com.trypod33.kitchlore"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trypod33.kitchlore"
        minSdk = 26
        targetSdk = 34
        versionCode = 47
        versionName = "0.5.14"
        buildConfigField("String", "MODEL_URL", "\"${prop("modelUrl", "https://huggingface.co/litert-community/SmolLM2-360M-Instruct/resolve/main/SmolLM2_360M_instruct.litertlm")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    signingConfigs {
        getByName("debug") {
            storeFile = sharedDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("shared") {
            storeFile = sharedDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes.getByName("release").signingConfig = signingConfigs.getByName("shared")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // First-scan model download + on-device inference (same stack as KidCompanion).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    implementation("com.google.mlkit:text-recognition:16.0.1")
    // CamScanner-style document scanning: edge detect, crop, perspective
    // straighten and clean the page before OCR. Google Play services, no key.
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.api-client:google-api-client-android:2.4.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20240509-2.0.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    testImplementation("junit:junit:4.13.2")
}

// Export Room schemas so every version bump gets a checked-in baseline and future
// migrations are written against real history instead of guesses.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
