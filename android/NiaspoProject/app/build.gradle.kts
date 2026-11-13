import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val apiUrl = providers.gradleProperty("taskApiUrl").orElse("http://10.0.2.2:8080").get()
val apiUri = URI(apiUrl)
require(
    apiUri.host != null && apiUri.rawUserInfo == null &&
        apiUri.rawQuery == null && apiUri.rawFragment == null &&
        (apiUri.scheme == "https" ||
            apiUri.scheme == "http" && apiUri.host in listOf("10.0.2.2", "127.0.0.1"))
) { "taskApiUrl должен быть базовым HTTP(S)-адресом API" }

android {
    namespace = "com.example.niaspoproject"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.niaspoproject"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "TASK_API_URL", "\"${apiUri.toASCIIString().trimEnd('/')}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}