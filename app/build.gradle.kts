import java.util.Properties
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    kotlin("plugin.serialization") version "2.4.10"
}

// Helper to get git commit count using standard Java ProcessBuilder to avoid Gradle API deprecations/scope issues
fun getGitCommitCount(): Int {
    return try {
        val process = ProcessBuilder("git", "rev-list", "--count", "HEAD").start()
        process.waitFor()
        val output = process.inputStream.bufferedReader().readText().trim()
        output.toIntOrNull() ?: 0
    } catch (e: Exception) {
        0
    }
}

// Load version.properties
val versionProps = Properties()
val versionPropsFile = file("../version.properties")
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
}

// Increment Build Number logic
var buildNum = versionProps.getProperty("build", "0").toInt()

// Check if we are running a build task
if (gradle.startParameter.taskNames.any { it.contains("assemble") || it.contains("build") || it.contains("install") }) {
    buildNum += 1
    versionProps.setProperty("build", buildNum.toString())
    versionProps.store(FileOutputStream(versionPropsFile), null)
}

val vMajor = versionProps.getProperty("major", "1").toInt()
val vMinor = versionProps.getProperty("minor", "0").toInt()
val patchOffset = versionProps.getProperty("patchOffset", "0").toInt()
val currentGitCount = getGitCommitCount()
val vPatch = (currentGitCount - patchOffset).coerceAtLeast(0)

// VersionCode: Major(2) Minor(2) Patch(2) Build(3) -> XX XX XX XXX
// Android versionCode is a signed 32-bit int (max 2,147,483,647).
val buildCode = (vMajor.toLong() * 10_000_000 + vMinor * 100_000 + vPatch * 1_000 + buildNum).also {
    require(it <= Int.MAX_VALUE) { "versionCode $it overflows 32-bit signed int" }
}.toInt()
val buildName = "$vMajor.$vMinor.$vPatch.$buildNum"

println("Configuring Build: Name=$buildName, Code=$buildCode")

android {
    namespace = "com.hereliesaz.blusnu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hereliesaz.blusnu"
        minSdk = 26
        targetSdk = 37
        versionCode = buildCode
        versionName = buildName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mapsApiKey = project.findProperty("MAPS_API_KEY")?.toString()
            ?: System.getenv("MAPS_API_KEY")
            ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey

        // Expose version details to code if needed
        buildConfigField("int", "VERSION_MAJOR", "$vMajor")
        buildConfigField("int", "VERSION_MINOR", "$vMinor")
        buildConfigField("int", "VERSION_PATCH", "$vPatch")
        buildConfigField("int", "BUILD_NUMBER", "$buildNum")
    }

    signingConfigs {
        val keystorePath = System.getenv("KEYSTORE_PATH")
        if (keystorePath != null && file(keystorePath).exists()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
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
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && file(keystorePath).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
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
    implementation(libs.play.services.location)
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.serialization)
    implementation("androidx.appcompat:appcompat:1.7.1")
}
