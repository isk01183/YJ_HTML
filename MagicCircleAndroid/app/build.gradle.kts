plugins {
    id("com.android.application")
}

android {
    namespace = "com.yj.magiccircle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yj.magiccircle"
        minSdk = 23
        targetSdk = 37
        // Increase code for every distributed APK, including restorations and hotfixes.
        versionCode = 13
        versionName = "1.10"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    bundle {
        language {
            // All three languages remain available offline, including native preview controls.
            enableSplit = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
