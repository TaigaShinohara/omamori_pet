plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.omamoripet.watchface"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.omamoripet.app.watchface"
        minSdk = 34
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
