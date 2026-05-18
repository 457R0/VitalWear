plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.cfogrady.vitalwear.common"
    compileSdk = 35

    defaultConfig {
        minSdk = 28

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
    buildFeatures {
        viewBinding = true
        compose = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // GIF
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.preview)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.livedata)
    implementation(libs.androidx.compose.foundation)
    androidTestImplementation(libs.androidx.compose.ui.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // VB-Dim-Reader library
    implementation(files("../../VB-DIM-Reader-2.0.0/build/libs/VB-DIM-Reader.jar"))
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("at.favre.lib:bytes:1.5.0")

    // Logging
    implementation(libs.timber)
    implementation(libs.tiny.log)

    // Needed
    implementation(libs.guava)

    // Rooms
    implementation(libs.room.runtime)
    // To use Kotlin Symbol Processing (KSP)
    ksp(libs.room.compile)
    // optional - Test helpers
    testImplementation(libs.room.testing)
}