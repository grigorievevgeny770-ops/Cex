import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val debugPrefillProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.reader(Charsets.UTF_8).use { load(it) }
}
fun buildConfigString(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

val releaseBaseUrl = providers.gradleProperty("releaseBaseUrl").orElse("https://example.invalid/").get()
require(URI(releaseBaseUrl).scheme == "https") { "releaseBaseUrl must use HTTPS" }
val fixtureBaseUrl = providers.gradleProperty("fixtureBaseUrl").orElse("http://127.0.0.1:9/").get()
require(URI(fixtureBaseUrl).scheme == "http" && URI(fixtureBaseUrl).host in setOf("10.0.2.2", "127.0.0.1", "localhost")) { "fixtureBaseUrl must be a local debug HTTP address" }
val fixtureBaseUrlLiteral = "\"" + fixtureBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
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
        buildConfigField("String", "DEBUG_PREFILL_LOGIN", "\"\"")
        buildConfigField("String", "DEBUG_PREFILL_PASSWORD", "\"\"")
        testInstrumentationRunner = "com.logunovinc.workshopmanager.testing.WorkshopTestRunner"
    }
    sourceSets.getByName("androidTest").assets.srcDir("../data/src/test/resources")
    buildTypes {
        debug {
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8000/\"")
            buildConfigField("String", "DEBUG_PREFILL_LOGIN", buildConfigString(debugPrefillProperties.getProperty("debug.prefill.login", "")))
            buildConfigField("String", "DEBUG_PREFILL_PASSWORD", buildConfigString(debugPrefillProperties.getProperty("debug.prefill.password", "")))
        }
        release { buildConfigField("String", "DEFAULT_BASE_URL", releaseBaseUrlLiteral) }
        create("processFixture") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".processfixture"
            buildConfigField("String", "DEBUG_PREFILL_LOGIN", "\"\"")
            buildConfigField("String", "DEBUG_PREFILL_PASSWORD", "\"\"")
            matchingFallbacks += listOf("debug")
            buildConfigField("String", "DEFAULT_BASE_URL", fixtureBaseUrlLiteral)
        }
    }
    sourceSets.getByName("processFixture") {
        manifest.srcFile("src/debug/AndroidManifest.xml")
        res.srcDir("src/debug/res")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }

dependencies {
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.room.runtime)
    androidTestImplementation(libs.room.ktx)
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
    implementation(libs.androidx.hilt.navigation)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
    debugImplementation(libs.compose.tooling)
}
