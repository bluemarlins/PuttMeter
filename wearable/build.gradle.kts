plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

android {
    namespace = "com.bluemarlin.puttmeter.wearable"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bluemarlin.puttmeter"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
    
    applicationVariants.all {
        val variant = this
        val buildDateTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val originalFile = output.outputFileName
            
            // APK 파일명 형식: PuttMeter-wearable-{buildType}-v{versionName}-{buildDateTime}.apk
            // 예: PuttMeter-wearable-debug-v1.0-20241016_143025.apk
            val newFileName = originalFile.replace(
                "wearable-",
                "wearable-${variant.buildType.name}-v${variant.versionName}-${buildDateTime}-"
            ).replace("--", "-")
            
            output.outputFileName = newFileName
        }
    }
}

dependencies {

    implementation(libs.play.services.wearable)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    implementation(libs.compose.foundation)
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.material3.android)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}