plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.aiyu.clavis"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiyu.clavis"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = rootProject.file("VERSION").readText().trim()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Side-by-side dev install, as in Domus.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Debug installs only on 64-bit phones and the emulator; ML Kit's native libs for 4 ABIs make the APK huge.
            ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "androidx.compose.animation.ExperimentalSharedTransitionApi",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":ui-common"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    // On-device OCR (D8). It bundles Google telemetry (datatransport); INTERNET is stripped in the manifest so it cannot send (D22).
    implementation(libs.mlkit.text.recognition)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.compose.ui.tooling)
}
