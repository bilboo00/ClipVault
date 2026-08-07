import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.clipvault.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clipvault.manager"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "1.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Optional release signing via local.properties (gitignored).
        // Add these keys to local.properties to enable signed release builds:
        //   CLIPVAULT_STORE_FILE=keystore.jks
        //   CLIPVAULT_STORE_PASSWORD=...
        //   CLIPVAULT_KEY_ALIAS=...
        //   CLIPVAULT_KEY_PASSWORD=...
        val signingProps = rootProject.file("local.properties").let { f ->
            if (f.exists()) Properties().apply { f.inputStream().use(::load) } else null
        }
        if (signingProps != null && signingProps.getProperty("CLIPVAULT_STORE_FILE") != null) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("CLIPVAULT_STORE_FILE"))
                storePassword = signingProps.getProperty("CLIPVAULT_STORE_PASSWORD")
                keyAlias = signingProps.getProperty("CLIPVAULT_KEY_ALIAS")
                keyPassword = signingProps.getProperty("CLIPVAULT_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingConfigs.findByName("release") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Glance widgets
    implementation("androidx.glance:glance-appwidget:1.1.0")

    // Biometric authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Open Graph / link preview parsing
    implementation("org.jsoup:jsoup:1.17.2")

    // AppCompat (required for BiometricPrompt with FragmentActivity)
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.08.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}