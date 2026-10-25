plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

val nativeArchive = file("libs/tailcatkit.aar")
val nativeEnabled = providers.gradleProperty("tailcat.enabled").orNull?.let {
    requireNotNull(it.toBooleanStrictOrNull()) { "tailcat.enabled must be true or false" }
} ?: nativeArchive.isFile
require(!nativeEnabled || nativeArchive.isFile) {
    "Tailcat AAR is missing. Run scripts/build-tailcat.sh or use -Ptailcat.enabled=false."
}

android {
    namespace = "dev.intent.tailcat"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets {
        getByName("main").kotlin.srcDir(
            if (nativeEnabled) "src/native/kotlin" else "src/stub/kotlin"
        )
        getByName("test").kotlin.srcDir(
            if (nativeEnabled) "src/testNative/kotlin" else "src/testStub/kotlin"
        )
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

dependencies {
    implementation(libs.kotlinx.serialization.json)
    if (nativeEnabled) implementation(project(":tailcat:native"))
    testImplementation(libs.junit)
}
