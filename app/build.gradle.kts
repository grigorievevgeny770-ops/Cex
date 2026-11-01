import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val releaseBaseUrl = providers.gradleProperty("releaseBaseUrl").orElse("https://example.invalid/").get()
require(URI(releaseBaseUrl).scheme == "https") { "releaseBaseUrl must use HTTPS" }
val releaseBaseUrlLiteral = "\"" + releaseBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.logunovinc.workshopmanager"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        applicationId = "com.logunovinc.workshopmanager"
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        debug { buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8000/\"") }
        release { buildConfigField("String", "DEFAULT_BASE_URL", releaseBaseUrlLiteral) }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:stock"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.viewmodel.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.activity.compose)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.androidx.navigation.compose)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
    debugImplementation(libs.compose.tooling)
}
