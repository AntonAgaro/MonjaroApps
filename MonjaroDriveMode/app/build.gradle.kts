plugins {
    id("com.android.application")
}

android {
    namespace = "app.monjaro.drivemode"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.monjaro.drivemode"
        // The head unit runs Android 11 (API 30).
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the local debug key so the APK can be installed with adb.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
