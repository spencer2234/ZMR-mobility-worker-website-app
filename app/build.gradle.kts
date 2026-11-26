import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.zmr.rent"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.zmr.rent"
        // Android 8.0. Old and low-cost phones are common in the workshop setting.
        minSdk = 26
        // Android 16. Raise it only after testing the app on the newer Android version.
        targetSdk = 36
        // Raise versionCode for every APK sent out, or phones will not install it as an update.
        versionCode = 2
        versionName = "1.0.0"
    }

    // The release key and its passwords live in keystore.properties, which is not in git.
    // Without that file a release build is unsigned, so it cannot be installed.
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        val keystore = Properties().apply { keystoreFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = file(keystore.getProperty("storeFile"))
            storePassword = keystore.getProperty("storePassword")
            keyAlias = keystore.getProperty("keyAlias")
            keyPassword = keystore.getProperty("keyPassword")
        }
    }

    androidResources {
        // Only ship the languages the app is translated into; keeps the APK small.
        localeFilters += listOf("en", "hi", "ta", "kn")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // BuildConfig.DEBUG hides guest mode in release builds.
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Robolectric needs the merged manifest and resources to run Room on the JVM.
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    // Room writes the schema of every database version here. The files are committed so that
    // schema changes show up in pull requests and migrations can be tested.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.coroutines.android)
    // Draws UPI payment QR codes on the phone, with no internet needed.
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.robolectric)
}
