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
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
