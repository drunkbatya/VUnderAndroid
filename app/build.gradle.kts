plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

fun git(vararg arguments: String): String? {
    val result = providers.exec {
        commandLine("git", *arguments)
        isIgnoreExitValue = true
    }
    return result.standardOutput.asText.get().trim().takeIf { result.result.get().exitValue == 0 }
}

val versionOrder = compareBy<List<Int>>({ it.getOrElse(0) { 0 } }, { it.getOrElse(1) { 0 } }, { it.getOrElse(2) { 0 } })

val releaseVersionName: String = git("tag", "--points-at", "HEAD", "--list", "[0-9]*")
    ?.lines()
    ?.filter { it.isNotBlank() }
    ?.maxWithOrNull(compareBy(versionOrder) { tag -> tag.split(".").map { it.toIntOrNull() ?: -1 } })
    ?: git("describe", "--tags", "--abbrev=0", "--match", "[0-9]*")
    ?: "0.0.0"

val releaseVersionCode: Int = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
    namespace = "com.drunkbatya.vunder"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.drunkbatya.vunder"
        minSdk = 34
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        create("release") {
            storeFile = providers.environmentVariable("VUNDER_KEYSTORE").orNull?.let(::file)
            storePassword = providers.environmentVariable("VUNDER_KEYSTORE_PASSWORD").orNull
            keyAlias = providers.environmentVariable("VUNDER_KEY_ALIAS").orNull
            keyPassword = providers.environmentVariable("VUNDER_KEY_PASSWORD").orNull
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
