
plugins {
    id("com.android.application")
    
}

android {
    namespace = "damjay.floating.projects"
    compileSdk = 33
    
    defaultConfig {
        applicationId = "damjay.floating.projects"
        minSdk = 19
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
        // Do NOT set vectorDrawables.useSupportLibrary = true.
        // With minSdk 19 the vector icons (play/pause/plus/minus/cancel/
        // fast_forward/fast_backward/copy_logo) must be rasterized to PNG
        // for pre-21 devices, otherwise the floating service layouts crash
        // on Android 4.4 (VectorDrawable is API 21+).
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
        }
    }

}

dependencies {


    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    testImplementation("junit:junit:4.12")
    testImplementation("org.mockito:mockito-core:1.10.19")
}
