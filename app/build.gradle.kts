import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Telegram API credentials come from local.properties (gitignored) so they never enter VCS.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val telegramApiId = localProps.getProperty("telegram.api.id")?.trim().takeUnless { it.isNullOrEmpty() } ?: "0"
val telegramApiHash = localProps.getProperty("telegram.api.hash")?.trim().orEmpty()

android {
    namespace = "app.yodai.telewear"
    compileSdk = 37

    // CI stamps builds with -PbuildNumber=<run number>; local builds are "-dev".
    val buildNumber = (findProperty("buildNumber") as? String)?.toIntOrNull() ?: 0

    defaultConfig {
        applicationId = "app.yodai.telewear"
        minSdk = 30
        targetSdk = 36
        versionCode = 1 + buildNumber
        versionName = if (buildNumber > 0) "1.1.$buildNumber" else "1.1.0-dev"

        buildConfigField("int", "TELEGRAM_API_ID", telegramApiId)
        buildConfigField("String", "TELEGRAM_API_HASH", "\"$telegramApiHash\"")

        // Samsung Wear OS watches run 32-bit userspace (abilist: armeabi-v7a,armeabi)
        // even on 64-bit silicon — the Galaxy Watch 8 accepts ONLY armeabi-v7a.
        // Add x86_64 here if you need the Wear emulator.
        ndk {
            abiFilters += listOf("armeabi-v7a")
        }
    }

    signingConfigs {
        // Committed keystore: NOT a secret — it only pins a stable signature so
        // CI-built releases install over local builds (personal sideload app).
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "telewear-release"
            keyAlias = "telewear"
            keyPassword = "telewear-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        // media3-ui APIs are @UnstableApi; usage is deliberate and contained.
        disable += "UnsafeOptInUsageError"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material.icons)

    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.wear.ongoing)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.wear.remote.interactions)
    implementation(libs.datastore.preferences)
    implementation(libs.zxing.core)

    implementation(libs.tdl.coroutines)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.lottie.compose)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.complications)
    implementation(libs.concurrent.futures.ktx)
}
