plugins {
    id("com.android.application")
}

android {
    namespace = "app.monjaro.carexplorer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.monjaro.carexplorer"
        // The head unit runs Android 11 (API 30).
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the local debug key so the APK can be installed with adb.
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            // The debug build bundles a fake car (src/debug) for the emulator.
            // Its own package id keeps it from ever replacing the real app on the car.
            applicationIdSuffix = ".fake"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
