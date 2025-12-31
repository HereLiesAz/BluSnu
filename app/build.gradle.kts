import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    kotlin("plugin.serialization") version "2.2.20"
}

// Load version.properties
val versionProps = Properties()
val versionPropsFile = file("../version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

val vMajor = versionProps.getProperty("major", "1")
val vMinor = versionProps.getProperty("minor", "0")
val vPatch = versionProps.getProperty("patch", "0")

// Calculate version code and name
// Priority: CI Properties -> version.properties
val buildCode = project.findProperty("versionCode")?.toString()?.toIntOrNull() ?: 1
val buildName = project.findProperty("versionName")?.toString() ?: "$vMajor.$vMinor.$vPatch-dev"

android {
    namespace = "com.hereliesaz.blusnu"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hereliesaz.blusnu"
        minSdk = 26
        targetSdk = 36
        versionCode = buildCode
        versionName = buildName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    buildFeatures {
        compose = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.gson)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.aznavrail)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.fragment)
    implementation(libs.osmdroid.android)
    implementation(libs.play.services.location)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.serialization)
}
