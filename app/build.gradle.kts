plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.verso.music"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.verso.music"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.4.1"
    }

    signingConfigs {
        // Release signing via environment (CI) or ~/.gradle/gradle.properties.
        // Created only when a keystore file is actually present, so plain
        // checkouts (e.g. F-Droid buildserver) build unsigned without edits.
        val f = System.getenv("VERSO_STORE_FILE")
            ?: System.getenv("LYRICTAG_STORE_FILE")
            ?: project.findProperty("VERSO_STORE_FILE") as? String
            ?: project.findProperty("LYRICTAG_STORE_FILE") as? String
        if (!f.isNullOrBlank() && file(f).exists()) {
            create("release") {
                storeFile = file(f)
                storePassword = System.getenv("VERSO_STORE_PASSWORD")
                    ?: System.getenv("LYRICTAG_STORE_PASSWORD")
                    ?: project.findProperty("VERSO_STORE_PASSWORD") as? String
                    ?: project.findProperty("LYRICTAG_STORE_PASSWORD") as? String
                keyAlias = System.getenv("VERSO_KEY_ALIAS")
                    ?: System.getenv("LYRICTAG_KEY_ALIAS")
                    ?: project.findProperty("VERSO_KEY_ALIAS") as? String
                    ?: project.findProperty("LYRICTAG_KEY_ALIAS") as? String
                keyPassword = System.getenv("VERSO_KEY_PASSWORD")
                    ?: System.getenv("LYRICTAG_KEY_PASSWORD")
                    ?: project.findProperty("VERSO_KEY_PASSWORD") as? String
                    ?: project.findProperty("LYRICTAG_KEY_PASSWORD") as? String
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
            // Only sign when credentials are actually configured.
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    buildFeatures { compose = true }
    // Compose compiler version is managed by the kotlin.compose plugin.

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Reproducible-build friendly: deterministic ordering is default in AGP 8.
        }
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.jaudiotagger)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
