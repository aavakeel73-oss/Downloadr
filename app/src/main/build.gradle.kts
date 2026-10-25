plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("com.google.devtools.ksp")
}
android {
 namespace = "com.tubesave.pro"
 compileSdk = 36
 defaultConfig { applicationId = "com.tubesave.pro"; minSdk = 26; targetSdk = 36; versionCode = 1; versionName = "1.0" }
 buildTypes { release { isMinifyEnabled = false } }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildFeatures { compose = true }
}
dependencies {
 val bom = platform("androidx.compose:compose-bom:2025.04.01")
 implementation(bom)
 implementation("androidx.activity:activity-compose:1.10.1")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.foundation:foundation")
 implementation("androidx.compose.material:material-icons-extended")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
 implementation("androidx.room:room-runtime:2.7.1")
 implementation("androidx.room:room-ktx:2.7.1")
 ksp("androidx.room:room-compiler:2.7.1")
 implementation("androidx.work:work-runtime-ktx:2.10.1")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.media3:media3-exoplayer:1.6.1")
 implementation("androidx.media3:media3-ui:1.6.1")
 implementation("androidx.core:core-ktx:1.16.0")
}
