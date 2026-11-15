import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Release signing: env vars (CI) win over the gitignored keystore.properties (local); neither = unsigned release.
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

fun signingValue(env: String, property: String): String? =
    System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(property)?.takeIf { it.isNotBlank() }

val keystoreFile = signingValue("ANDROID_KEYSTORE_FILE", "storeFile")?.let(rootProject::file)
val appVersionName = rootProject.file("VERSION").readText().trim()

/**
 * X.Y.Z[-alpha|beta.N] -> a code that rises with every release: 1.4.2-beta.3 = 10402103, 1.4.2 = 10402200.
 * CLAVIS_BUILD (set by a `!patch` rebuild) fills a stable release's last two digits.
 */
fun versionCodeOf(name: String, build: Int = System.getenv("CLAVIS_BUILD")?.toIntOrNull() ?: 0): Int {
    val m = Regex("""^(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""").matchEntire(name)
        ?: error("VERSION '$name' is not X.Y.Z or X.Y.Z-alpha|beta.N")
    val (major, minor, patch, label, number) = m.destructured
    require(minor.toInt() < 100 && patch.toInt() < 100 && (number.toIntOrNull() ?: 0) < 100 && build in 0..99) { "VERSION '$name' overflows a version code slot" }
    val stage = when (label) { "alpha" -> 0; "beta" -> 1; else -> 2 }
    val tail = if (label.isEmpty()) build else number.toInt()
    return major.toInt() * 10_000_000 + minor.toInt() * 100_000 + patch.toInt() * 1_000 + stage * 100 + tail
}

android {
    namespace = "com.aiyu.clavis"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.aiyu.clavis"
        minSdk = 28
        targetSdk = 36
        versionCode = versionCodeOf(appVersionName)
        versionName = appVersionName
    }

    // English only: drops the translations that libraries (Material, ML Kit, CameraX) ship.
    androidResources { localeFilters += listOf("en") }

    signingConfigs {
        if (keystoreFile?.exists() == true) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            // Phones are arm; x86 ML Kit libraries only add megabytes.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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

    packaging {
        resources.excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "/META-INF/*.kotlin_module", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
        jniLibs.useLegacyPackaging = false
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
