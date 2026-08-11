import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Signing credentials come from keystore.properties when building locally and from the
 * environment when building in CI. Neither is in the repository, and a build with neither still
 * succeeds — it just produces an unsigned APK, which is honest rather than broken.
 */
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun credential(property: String, environment: String): String? =
    keystoreProperties.getProperty(property) ?: System.getenv(environment)

android {
    namespace = "com.shadatrahman.bikemode"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.shadatrahman.bikemode"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val store = credential("storeFile", "KEYSTORE_FILE")
            // Left unconfigured when there is no keystore, which the release block then detects.
            if (store != null) {
                storeFile = file(store)
                storePassword = credential("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = credential("keyAlias", "KEY_ALIAS")
                keyPassword = credential("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Distributed as an APK from GitHub rather than through Play, so the download is the
            // artifact: worth shrinking, and there is no dynamic delivery to complicate it.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.getByName("release")
                .takeIf { it.storeFile != null }
                ?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}