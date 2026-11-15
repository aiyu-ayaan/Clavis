plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.aiyu.clavis.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }
}

dependencies {
    testImplementation(libs.junit)
}
