import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.pillbox"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pillbox"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        // Only the languages we ship; drops translations pulled in by libraries.
        resourceConfigurations += listOf("en", "ar")
    }

    // One fixed debug key (the public Android debug key pattern) so every CI build is signed the same
    // and a new test build installs over the old one. NOT for release builds.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // Release key: never in the repository. Set these four environment variables (or Gradle properties) on the
        // machine that builds the release; without them the release build is simply left unsigned.
        val releaseFile = (System.getenv("PILLBOX_KEYSTORE") ?: findProperty("pillbox.keystore") as String?)
        if (releaseFile != null) {
            create("release") {
                storeFile = file(releaseFile)
                storePassword = System.getenv("PILLBOX_STORE_PASSWORD") ?: findProperty("pillbox.storePassword") as String?
                keyAlias = System.getenv("PILLBOX_KEY_ALIAS") ?: findProperty("pillbox.keyAlias") as String?
                keyPassword = System.getenv("PILLBOX_KEY_PASSWORD") ?: findProperty("pillbox.keyPassword") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Full failure details in the CI log, so a failing test can be diagnosed without opening the report.
        unitTests.all {
            it.testLogging {
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                showCauses = true
                showStackTraces = true
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.play.services.auth)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}
