plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.mattshealthtracker"
    compileSdk = 36

    packaging {
        resources { // <--- ADD this resources block
            excludes.add("META-INF/DEPENDENCIES") // <--- Use excludes.add() inside resources
        }
    }

    defaultConfig {
        applicationId = "com.example.mattshealthtracker"
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "1.8.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.animation.android)
    implementation(libs.places)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.android.material)
    implementation("androidx.compose.material3:material3-android:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.20.0")
    implementation("com.google.api-client:google-api-client-android:1.34.0")
    implementation("com.google.android.gms:play-services-drive:17.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.41.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("androidx.health.connect:connect-client:1.1.0-rc03") // Check for the latest stable alpha version if available
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

}