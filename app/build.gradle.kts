plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.vwillcox.mytv"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.vwillcox.mytv"
        // Android TV 8.0: the first with preview channels and Watch Next, which the home screen is built from.
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        // AllMedia's key, which AllMedia's copy on the TV is signed with. The Android tools keep a debug key in more
        // than one place (~/.config/.android as well), and the two apps share their library and who is watching only
        // when they are signed alike.
        getByName("debug") {
            val key = File(System.getProperty("user.home"), ".android/debug.keystore")
            if (key.exists()) storeFile = key
        }
    }

    buildTypes {
        release {
            // Compose is several times slower without R8, so this is the build to put on the TV.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/{AL2.0,LGPL2.1}", "META-INF/nanohttpd/**", "META-INF/DEPENDENCIES", "META-INF/{LICENSE,NOTICE}{,.md,.txt}")
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.tvprovider)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)

    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    // The frosted glass of the top bar: the page behind it, blurred.
    implementation(libs.haze)
    implementation(libs.haze.blur)
}
