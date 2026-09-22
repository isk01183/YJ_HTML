plugins {
    id("com.android.application")
}

// Keep old source archives on disk, but the review APK renders all 118 codes directly.
androidComponents {
    onVariants { variant ->
        variant.androidResources.ignoreAssetsPatterns.add("!*.svgz")
    }
}

android {
    namespace = "com.yj.magiccircle"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.yj.magiccircle"
        minSdk = 23
        targetSdk = 37
        // Increase code for every distributed APK, including restorations and hotfixes.
        versionCode = 16
        versionName = "1.13"
        testInstrumentationRunner = "com.yj.magiccircle.V113Instrumentation"
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

dependencies {
    testImplementation("junit:junit:4.13.2")
}
