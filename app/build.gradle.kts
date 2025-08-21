plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)

    // Google services Gradle plugin
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.billkmotolinkltd"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.billkmotolinkltd"
        minSdk = 24
        targetSdk = 35
        versionCode = 6
        versionName = "1.3.10.2" // changed 21st Aug, Thu

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.media3.common.ktx)
    implementation(libs.androidx.ui.text)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.11.0"))

    // When using the BoM, you don't specify versions in Firebase library dependencies

    // Add the dependency for the Firebase SDK for Google Analytics
    implementation("com.google.firebase:firebase-analytics")

    // TODO: Add the dependencies for any other Firebase products you want to use
    // See https://firebase.google.com/docs/android/setup#available-libraries
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-inappmessaging")
    implementation("com.google.firebase:firebase-inappmessaging-display")
    implementation("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-firestore-ktx:24.3.0")
    implementation("com.google.firebase:firebase-messaging:23.4.1")

    // Firebase BoM
    implementation (platform("com.google.firebase:firebase-bom:32.7.0"))

    // android
    implementation("com.android.volley:volley:1.2.1")

    // location
    implementation ("com.google.android.gms:play-services-location:21.0.1")
    implementation ("com.squareup.okhttp3:okhttp:4.12.0")

    // card view
    implementation ("androidx.cardview:cardview:1.0.0")

    // shimmer
    implementation ("com.facebook.shimmer:shimmer:0.5.0")

    // pie charts
    implementation ("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // animation
    implementation ("androidx.core:core-ktx:1.6.0") // or newer

    // biometrics
    implementation("androidx.biometric:biometric:1.4.0-alpha02")

    // notifications
    implementation ("com.google.firebase:firebase-messaging-ktx:23.4.0") // Use latest version
    implementation ("com.google.firebase:firebase-firestore-ktx:24.10.0")

    // swipe refresh
    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // lifecycle for coroutines
    implementation ("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    /*for asyncTask*/
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.google.android.material:material:1.12.0")

    /*database*/
    implementation("com.google.firebase:firebase-database-ktx:20.3.0")
    implementation("com.firebaseui:firebase-ui-database:8.0.2")

}