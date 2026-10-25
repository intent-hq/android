plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val releaseVersion = providers.gradleProperty("appVersion").get()
val versionParts = releaseVersion.split(".")
require(versionParts.size == 3 && versionParts.all { it.matches(Regex("0|[1-9][0-9]{0,2}")) }) {
    "appVersion must be MAJOR.MINOR.PATCH with each number between 0 and 999"
}
val (majorVersion, minorVersion, patchVersion) = versionParts.map(String::toInt)
val releaseVersionCode = majorVersion * 1_000_000 + minorVersion * 1_000 + patchVersion + 1

android {
    namespace = "dev.intent.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.intent.app"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        minSdk = 26
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersion
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        animationsDisabled = true
        managedDevices.localDevices.create("pixel8Api34") {
            device = "Pixel 8"
            apiLevel = 34
            systemImageSource = "google"
        }
    }

    buildFeatures {
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt.yml"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.commonmark)
    implementation(libs.commonmark.tables)
    implementation(libs.commonmark.tasks)
    implementation(libs.zxing)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okhttp.tls)
    implementation(project(":core:protocol"))
    implementation(project(":tailcat"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
}
