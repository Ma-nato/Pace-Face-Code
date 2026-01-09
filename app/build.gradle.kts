//app/src/build.gradle.kts(:app)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
    alias(libs.plugins.googleGmsServices) // google.gms.services -> googleGmsServices に修正
}

android {
    namespace = "com.example.paceface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.paceface"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

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
        viewBinding = true   // ここで ViewBinding を有効化
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    val room_version = "2.8.4"

    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    kapt("androidx.room:room-compiler:$room_version")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Coroutines for Play Services await()
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.0") // ★★★ これがフリーズを解決する鍵 ★★★

    // 基本ライブラリ
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.core:core-splashscreen:1.0.1") // Splash screen API (安定版に変更)
    implementation("androidx.appcompat:appcompat:1.6.1") // AppCompatActivity
    implementation("com.google.android.material:material:1.11.0") // Material Components
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Compose 関連
    implementation(libs.androidx.activity.compose)
    // テスト
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)


    implementation("com.google.android.gms:play-services-auth:21.0.0")
}
